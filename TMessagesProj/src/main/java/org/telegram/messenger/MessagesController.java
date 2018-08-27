/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
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
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.DialogsActivity;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;

public class MessagesController implements NotificationCenter.NotificationCenterDelegate {

    private ConcurrentHashMap<Integer, TLRPC.Chat> chats = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.EncryptedChat> encryptedChats = new ConcurrentHashMap<>(10, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.User> users = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<String, TLObject> objectsByUsernames = new ConcurrentHashMap<>(100, 1.0f, 2);

    private ArrayList<Integer> joiningToChannels = new ArrayList<>();

    private SparseArray<TLRPC.ExportedChatInvite> exportedChats = new SparseArray<>();

    public ArrayList<TLRPC.RecentMeUrl> hintDialogs = new ArrayList<>();
    public ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<>();
    public ArrayList<TLRPC.TL_dialog> dialogsForward = new ArrayList<>();
    public ArrayList<TLRPC.TL_dialog> dialogsServerOnly = new ArrayList<>();
    public ArrayList<TLRPC.TL_dialog> dialogsGroupsOnly = new ArrayList<>();
    public int unreadUnmutedDialogs;
    public int nextDialogsCacheOffset;
    public ConcurrentHashMap<Long, Integer> dialogs_read_inbox_max = new ConcurrentHashMap<>(100, 1.0f, 2);
    public ConcurrentHashMap<Long, Integer> dialogs_read_outbox_max = new ConcurrentHashMap<>(100, 1.0f, 2);
    public LongSparseArray<TLRPC.TL_dialog> dialogs_dict = new LongSparseArray<>();
    public LongSparseArray<MessageObject> dialogMessage = new LongSparseArray<>();
    public LongSparseArray<MessageObject> dialogMessagesByRandomIds = new LongSparseArray<>();
    public SparseArray<MessageObject> dialogMessagesByIds = new SparseArray<>();
    public ConcurrentHashMap<Long, ArrayList<PrintingUser>> printingUsers = new ConcurrentHashMap<>(20, 1.0f, 2);
    public LongSparseArray<CharSequence> printingStrings = new LongSparseArray<>();
    public LongSparseArray<Integer> printingStringsTypes = new LongSparseArray<>();
    public SparseArray<LongSparseArray<Boolean>> sendingTypings = new SparseArray<>();
    public ConcurrentHashMap<Integer, Integer> onlinePrivacy = new ConcurrentHashMap<>(20, 1.0f, 2);
    private int lastPrintingStringCount;

    private LongSparseArray<Boolean> loadingPeerSettings = new LongSparseArray<>();

    private ArrayList<Long> createdDialogIds = new ArrayList<>();
    private ArrayList<Long> createdDialogMainThreadIds = new ArrayList<>();
    private ArrayList<Long> visibleDialogMainThreadIds = new ArrayList<>();

    private SparseIntArray shortPollChannels = new SparseIntArray();
    private SparseIntArray needShortPollChannels = new SparseIntArray();

    public boolean loadingBlockedUsers = false;
    public SparseIntArray blockedUsers = new SparseIntArray();

    private SparseArray<ArrayList<Integer>> channelViewsToSend = new SparseArray<>();
    private long lastViewsCheckTime;

    private SparseArray<ArrayList<TLRPC.Updates>> updatesQueueChannels = new SparseArray<>();
    private SparseLongArray updatesStartWaitTimeChannels = new SparseLongArray();
    private SparseIntArray channelsPts = new SparseIntArray();
    private SparseBooleanArray gettingDifferenceChannels = new SparseBooleanArray();

    private SparseBooleanArray gettingUnknownChannels = new SparseBooleanArray();
    private SparseBooleanArray checkingLastMessagesDialogs = new SparseBooleanArray();

    private ArrayList<TLRPC.Updates> updatesQueueSeq = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueuePts = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueueQts = new ArrayList<>();
    private long updatesStartWaitTimeSeq;
    private long updatesStartWaitTimePts;
    private long updatesStartWaitTimeQts;
    private SparseArray<TLRPC.TL_userFull> fullUsers = new SparseArray<>();
    private ArrayList<Integer> loadingFullUsers = new ArrayList<>();
    private ArrayList<Integer> loadedFullUsers = new ArrayList<>();
    private ArrayList<Integer> loadingFullChats = new ArrayList<>();
    private ArrayList<Integer> loadingFullParticipants = new ArrayList<>();
    private ArrayList<Integer> loadedFullParticipants = new ArrayList<>();
    private ArrayList<Integer> loadedFullChats = new ArrayList<>();
    private SparseArray<ArrayList<Integer>> channelAdmins = new SparseArray<>();
    private SparseIntArray loadingChannelAdmins = new SparseIntArray();

    private HashMap<String, ArrayList<MessageObject>> reloadingWebpages = new HashMap<>();
    private LongSparseArray<ArrayList<MessageObject>> reloadingWebpagesPending = new LongSparseArray<>();

    private LongSparseArray<ArrayList<Integer>> reloadingMessages = new LongSparseArray<>();

    private ArrayList<ReadTask> readTasks = new ArrayList<>();
    private LongSparseArray<ReadTask> readTasksMap = new LongSparseArray<>();

    private boolean gettingNewDeleteTask;
    private int currentDeletingTaskTime;
    private ArrayList<Integer> currentDeletingTaskMids;
    private int currentDeletingTaskChannelId;
    private Runnable currentDeleteTaskRunnable;

    private boolean loadingUnreadDialogs;
    public boolean loadingDialogs;
    private boolean migratingDialogs;
    public boolean dialogsEndReached;
    public boolean serverDialogsEndReached;
    public boolean gettingDifference;
    private boolean getDifferenceFirstSync = true;
    public boolean updatingState;
    public boolean firstGettingTask;
    public boolean registeringForPush;
    private long lastPushRegisterSendTime;
    private boolean resetingDialogs;
    private TLRPC.TL_messages_peerDialogs resetDialogsPinned;
    private TLRPC.messages_Dialogs resetDialogsAll;

    private int loadingNotificationSettings;

    private int nextProxyInfoCheckTime;
    private boolean checkingProxyInfo;
    private int checkingProxyInfoRequestId;
    private TLRPC.TL_dialog proxyDialog;
    private boolean isLeftProxyChannel;
    private long proxyDialogId;

    private boolean checkingTosUpdate;
    private int nextTosCheckTime;

    public int secretWebpagePreview;
    public boolean suggestContacts = true;

    private volatile static long lastThemeCheckTime;
    private Runnable themeCheckRunnable = Theme::checkAutoNightThemeConditions;

    private volatile static long lastPasswordCheckTime;
    private Runnable passwordCheckRunnable = new Runnable() {
        @Override
        public void run() {
            UserConfig.getInstance(currentAccount).checkSavedPassword();
        }
    };

    private long lastStatusUpdateTime;
    private int statusRequest;
    private int statusSettingState;
    private boolean offlineSent;
    private String uploadingAvatar;

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
    public int mapProvider;
    public int availableMapProviders;
    public String mapKey;
    public int maxMessageLength;
    public int maxCaptionLength;
    public boolean blockedCountry;
    public boolean defaultP2pContacts;
    public boolean preloadFeaturedStickers;
    public String venueSearchBot;
    public String gifSearchBot;
    public String imageSearchBot;
    public String dcDomainName;
    public int webFileDatacenterId;
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
    public static final int UPDATE_MASK_CHANNEL = 8192;
    public static final int UPDATE_MASK_CHAT_ADMINS = 16384;
    public static final int UPDATE_MASK_MESSAGE_TEXT = 32768;
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

    private final Comparator<TLRPC.TL_dialog> dialogComparator = new Comparator<TLRPC.TL_dialog>() {
        @Override
        public int compare(TLRPC.TL_dialog dialog1, TLRPC.TL_dialog dialog2) {
            if (!dialog1.pinned && dialog2.pinned) {
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
            TLRPC.DraftMessage draftMessage = DataQuery.getInstance(currentAccount).getDraft(dialog1.id);
            int date1 = draftMessage != null && draftMessage.date >= dialog1.last_message_date ? draftMessage.date : dialog1.last_message_date;
            draftMessage = DataQuery.getInstance(currentAccount).getDraft(dialog2.id);
            int date2 = draftMessage != null && draftMessage.date >= dialog2.last_message_date ? draftMessage.date : dialog2.last_message_date;
            if (date1 < date2) {
                return 1;
            } else if (date1 > date2) {
                return -1;
            }
            return 0;
        }
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

    private int currentAccount;
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
        currentAccount = num;
        ImageLoader.getInstance();
        MessagesStorage.getInstance(currentAccount);
        LocationController.getInstance(currentAccount);
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController messagesController = getInstance(currentAccount);
            NotificationCenter.getInstance(currentAccount).addObserver(messagesController, NotificationCenter.FileDidUpload);
            NotificationCenter.getInstance(currentAccount).addObserver(messagesController, NotificationCenter.FileDidFailUpload);
            NotificationCenter.getInstance(currentAccount).addObserver(messagesController, NotificationCenter.FileDidLoaded);
            NotificationCenter.getInstance(currentAccount).addObserver(messagesController, NotificationCenter.FileDidFailedLoad);
            NotificationCenter.getInstance(currentAccount).addObserver(messagesController, NotificationCenter.messageReceivedByServer);
            NotificationCenter.getInstance(currentAccount).addObserver(messagesController, NotificationCenter.updateMessageMedia);
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
        maxMessageLength = mainPreferences.getInt("maxMessageLength", 4096);
        maxCaptionLength = mainPreferences.getInt("maxCaptionLength", 200);
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
        nextTosCheckTime = notificationsPreferences.getInt("nextTosCheckTime", 0);
        venueSearchBot = mainPreferences.getString("venueSearchBot", "foursquare");
        gifSearchBot = mainPreferences.getString("gifSearchBot", "gif");
        imageSearchBot = mainPreferences.getString("imageSearchBot", "pic");
        blockedCountry = mainPreferences.getBoolean("blockedCountry", false);
        dcDomainName = mainPreferences.getString("dcDomainName", ConnectionsManager.native_isTestBackend(currentAccount) != 0 ? "tapv2.stel.com" : "apv2.stel.com");
        webFileDatacenterId = mainPreferences.getInt("webFileDatacenterId", ConnectionsManager.native_isTestBackend(currentAccount) != 0 ? 2 : 4);
    }

    public void updateConfig(final TLRPC.TL_config config) {
        AndroidUtilities.runOnUIThread(() -> {
            LocaleController.getInstance().loadRemoteLanguages(currentAccount);
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
            editor.putString("dcDomainName", dcDomainName);
            editor.putInt("webFileDatacenterId", webFileDatacenterId);
            editor.commit();

            LocaleController.getInstance().checkUpdateForCurrentRemoteLocale(currentAccount, config.lang_pack_version);
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
        if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
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
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.User user = getUser(UserConfig.getInstance(currentAccount).getClientUserId());
                        if (user == null) {
                            user = UserConfig.getInstance(currentAccount).getCurrentUser();
                            putUser(user, true);
                        } else {
                            UserConfig.getInstance(currentAccount).setCurrentUser(user);
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
                        MessagesStorage.getInstance(currentAccount).clearUserPhotos(user.id);
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true);
                        AndroidUtilities.runOnUIThread(() -> {
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_AVATAR);
                            UserConfig.getInstance(currentAccount).saveConfig(true);
                        });
                    }
                });
            }
        } else if (id == NotificationCenter.FileDidFailUpload) {
            final String location = (String) args[0];
            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                uploadingAvatar = null;
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer) args[0];
            Integer newMsgId = (Integer) args[1];
            Long did = (Long) args[3];
            MessageObject obj = dialogMessage.get(did);
            if (obj != null && (obj.getId() == msgId || obj.messageOwner.local_id == msgId)) {
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
            }
            TLRPC.TL_dialog dialog = dialogs_dict.get(did);
            if (dialog != null && dialog.top_message == msgId) {
                dialog.top_message = newMsgId;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
            }
            obj = dialogMessagesByIds.get(msgId);
            dialogMessagesByIds.remove(msgId);
            if (obj != null) {
                dialogMessagesByIds.put(newMsgId, obj);
            }
        } else if (id == NotificationCenter.updateMessageMedia) {
            TLRPC.Message message = (TLRPC.Message) args[0];
            MessageObject existMessageObject = dialogMessagesByIds.get(message.id);
            if (existMessageObject != null) {
                existMessageObject.messageOwner.media = message.media;
                if (message.media.ttl_seconds != 0 && (message.media.photo instanceof TLRPC.TL_photoEmpty || message.media.document instanceof TLRPC.TL_documentEmpty)) {
                    existMessageObject.setType();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                }
            }
        }
    }

    public void cleanup() {
        ContactsController.getInstance(currentAccount).cleanup();
        MediaController.getInstance().cleanup();
        NotificationsController.getInstance(currentAccount).cleanup();
        SendMessagesHelper.getInstance(currentAccount).cleanup();
        SecretChatHelper.getInstance(currentAccount).cleanup();
        LocationController.getInstance(currentAccount).cleanup();
        DataQuery.getInstance(currentAccount).cleanup();

        DialogsActivity.dialogsLoaded[currentAccount] = false;

        SharedPreferences.Editor editor = notificationsPreferences.edit();
        editor.clear().commit();
        editor = emojiPreferences.edit();
        editor.putLong("lastGifLoadTime", 0).putLong("lastStickersLoadTime", 0).putLong("lastStickersLoadTimeMask", 0).putLong("lastStickersLoadTimeFavs", 0).commit();
        editor = mainPreferences.edit();
        editor.remove("gifhint").remove("dcDomainName").remove("webFileDatacenterId").commit();

        reloadingWebpages.clear();
        reloadingWebpagesPending.clear();
        dialogs_dict.clear();
        dialogs_read_inbox_max.clear();
        dialogs_read_outbox_max.clear();
        exportedChats.clear();
        fullUsers.clear();
        dialogs.clear();
        unreadUnmutedDialogs = 0;
        joiningToChannels.clear();
        channelViewsToSend.clear();
        dialogsServerOnly.clear();
        dialogsForward.clear();
        dialogsGroupsOnly.clear();
        dialogMessagesByIds.clear();
        dialogMessagesByRandomIds.clear();
        channelAdmins.clear();
        loadingChannelAdmins.clear();
        users.clear();
        objectsByUsernames.clear();
        chats.clear();
        dialogMessage.clear();
        printingUsers.clear();
        printingStrings.clear();
        printingStringsTypes.clear();
        onlinePrivacy.clear();
        loadingPeerSettings.clear();
        lastPrintingStringCount = 0;
        nextDialogsCacheOffset = 0;
        Utilities.stageQueue.postRunnable(() -> {
            readTasks.clear();
            readTasksMap.clear();
            updatesQueueSeq.clear();
            updatesQueuePts.clear();
            updatesQueueQts.clear();
            gettingUnknownChannels.clear();
            updatesStartWaitTimeSeq = 0;
            updatesStartWaitTimePts = 0;
            updatesStartWaitTimeQts = 0;
            createdDialogIds.clear();
            gettingDifference = false;
            resetDialogsPinned = null;
            resetDialogsAll = null;
        });
        createdDialogMainThreadIds.clear();
        visibleDialogMainThreadIds.clear();
        blockedUsers.clear();
        sendingTypings.clear();
        loadingFullUsers.clear();
        loadedFullUsers.clear();
        reloadingMessages.clear();
        loadingFullChats.clear();
        loadingFullParticipants.clear();
        loadedFullParticipants.clear();
        loadedFullChats.clear();

        checkingTosUpdate = false;
        nextTosCheckTime = 0;
        nextProxyInfoCheckTime = 0;
        checkingProxyInfo = false;
        loadingUnreadDialogs = false;

        currentDeletingTaskTime = 0;
        currentDeletingTaskMids = null;
        currentDeletingTaskChannelId = 0;
        gettingNewDeleteTask = false;
        loadingDialogs = false;
        dialogsEndReached = false;
        serverDialogsEndReached = false;
        loadingBlockedUsers = false;
        firstGettingTask = false;
        updatingState = false;
        resetingDialogs = false;
        lastStatusUpdateTime = 0;
        offlineSent = false;
        registeringForPush = false;
        getDifferenceFirstSync = true;
        uploadingAvatar = null;
        statusRequest = 0;
        statusSettingState = 0;

        Utilities.stageQueue.postRunnable(() -> {
            ConnectionsManager.getInstance(currentAccount).setIsUpdating(false);
            updatesQueueChannels.clear();
            updatesStartWaitTimeChannels.clear();
            gettingDifferenceChannels.clear();
            channelsPts.clear();
            shortPollChannels.clear();
            needShortPollChannels.clear();
        });

        if (currentDeleteTaskRunnable != null) {
            Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            currentDeleteTaskRunnable = null;
        }

        addSupportUser();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
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
            MessagesStorage.getInstance(currentAccount).getEncryptedChat(chat_id, countDownLatch, result);
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

    public boolean isDialogCreated(long dialog_id) {
        return createdDialogMainThreadIds.contains(dialog_id);
    }

    public boolean isDialogVisible(long dialog_id) {
        return visibleDialogMainThreadIds.contains(dialog_id);
    }

    public void setLastVisibleDialogId(final long dialog_id, final boolean set) {
        if (set) {
            if (visibleDialogMainThreadIds.contains(dialog_id)) {
                return;
            }
            visibleDialogMainThreadIds.add(dialog_id);
        } else {
            visibleDialogMainThreadIds.remove(dialog_id);
        }
    }

    public void setLastCreatedDialogId(final long dialog_id, final boolean set) {
        if (set) {
            if (createdDialogMainThreadIds.contains(dialog_id)) {
                return;
            }
            createdDialogMainThreadIds.add(dialog_id);
        } else {
            createdDialogMainThreadIds.remove(dialog_id);
        }
        Utilities.stageQueue.postRunnable(() -> {
            if (set) {
                if (createdDialogIds.contains(dialog_id)) {
                    return;
                }
                createdDialogIds.add(dialog_id);
            } else {
                createdDialogIds.remove(dialog_id);
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
                if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    UserConfig.getInstance(currentAccount).setCurrentUser(user);
                    UserConfig.getInstance(currentAccount).saveConfig(true);
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
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS));
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
                    oldChat.democracy = chat.democracy;
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
                    if (oldFlags != newFlags) {
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.channelRightsUpdated, chat));
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
                chat.democracy = oldChat.democracy;
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

    public TLRPC.TL_userFull getUserFull(int uid) {
        return fullUsers.get(uid);
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

    private void reloadDialogsReadValue(ArrayList<TLRPC.TL_dialog> dialogs, long did) {
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                for (int a = 0; a < res.dialogs.size(); a++) {
                    TLRPC.TL_dialog dialog = res.dialogs.get(a);
                    if (dialog.read_inbox_max_id == 0) {
                        dialog.read_inbox_max_id = 1;
                    }
                    if (dialog.read_outbox_max_id == 0) {
                        dialog.read_outbox_max_id = 1;
                    }
                    if (dialog.id == 0 && dialog.peer != null) {
                        if (dialog.peer.user_id != 0) {
                            dialog.id = dialog.peer.user_id;
                        } else if (dialog.peer.chat_id != 0) {
                            dialog.id = -dialog.peer.chat_id;
                        } else if (dialog.peer.channel_id != 0) {
                            dialog.id = -dialog.peer.channel_id;
                        }
                    }

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
                    processUpdateArray(arrayList, null, null, false);
                }
            }
        });
    }

    public boolean isChannelAdmin(int chatId, int uid) {
        ArrayList<Integer> array = channelAdmins.get(chatId);
        return array != null && array.indexOf(uid) >= 0;
    }

    public void loadChannelAdmins(final int chatId, final boolean cache) {
        if (loadingChannelAdmins.indexOfKey(chatId) >= 0) {
            return;
        }
        loadingChannelAdmins.put(chatId, 0);
        if (cache) {
            MessagesStorage.getInstance(currentAccount).loadChannelAdmins(chatId);
        } else {
            TLRPC.TL_channels_getParticipants req = new TLRPC.TL_channels_getParticipants();
            ArrayList<Integer> array = channelAdmins.get(chatId);
            if (array != null) {
                long acc = 0;
                for (int a = 0; a < array.size(); a++) {
                    acc = ((acc * 20261) + 0x80000000L + array.get(a)) % 0x80000000L;
                }
                req.hash = (int) acc;
            }
            req.channel = getInputChannel(chatId);
            req.limit = 100;
            req.filter = new TLRPC.TL_channelParticipantsAdmins();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_channels_channelParticipants) {
                    TLRPC.TL_channels_channelParticipants participants = (TLRPC.TL_channels_channelParticipants) response;
                    final ArrayList<Integer> array1 = new ArrayList<>(participants.participants.size());
                    for (int a = 0; a < participants.participants.size(); a++) {
                        array1.add(participants.participants.get(a).user_id);
                    }
                    processLoadedChannelAdmins(array1, chatId, false);
                }
            });
        }
    }

    public void processLoadedChannelAdmins(final ArrayList<Integer> array, final int chatId, final boolean cache) {
        Collections.sort(array);
        if (!cache) {
            MessagesStorage.getInstance(currentAccount).putChannelAdmins(chatId, array);
        }
        AndroidUtilities.runOnUIThread(() -> {
            loadingChannelAdmins.delete(chatId);
            channelAdmins.put(chatId, array);
            if (cache) {
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
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
            if (error == null) {
                final TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull) response;
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                MessagesStorage.getInstance(currentAccount).updateChatInfo(res.full_chat, false);

                if (ChatObject.isChannel(chat)) {
                    Integer value = dialogs_read_inbox_max.get(dialog_id);
                    if (value == null) {
                        value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
                    }

                    dialogs_read_inbox_max.put(dialog_id, Math.max(res.full_chat.read_inbox_max_id, value));
                    if (value == 0) {
                        ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                        TLRPC.TL_updateReadChannelInbox update = new TLRPC.TL_updateReadChannelInbox();
                        update.channel_id = chat_id;
                        update.max_id = res.full_chat.read_inbox_max_id;
                        arrayList.add(update);
                        processUpdateArray(arrayList, null, null, false);
                    }

                    value = dialogs_read_outbox_max.get(dialog_id);
                    if (value == null) {
                        value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, dialog_id);
                    }
                    dialogs_read_outbox_max.put(dialog_id, Math.max(res.full_chat.read_outbox_max_id, value));
                    if (value == 0) {
                        ArrayList<TLRPC.Update> arrayList = new ArrayList<>();
                        TLRPC.TL_updateReadChannelOutbox update = new TLRPC.TL_updateReadChannelOutbox();
                        update.channel_id = chat_id;
                        update.max_id = res.full_chat.read_outbox_max_id;
                        arrayList.add(update);
                        processUpdateArray(arrayList, null, null, false);
                    }
                }

                AndroidUtilities.runOnUIThread(() -> {
                    applyDialogNotificationsSettings(-chat_id, res.full_chat.notify_settings);
                    for (int a = 0; a < res.full_chat.bot_info.size(); a++) {
                        TLRPC.BotInfo botInfo = res.full_chat.bot_info.get(a);
                        DataQuery.getInstance(currentAccount).putBotInfo(botInfo);
                    }
                    exportedChats.put(chat_id, res.full_chat.exported_invite);
                    loadingFullChats.remove((Integer) chat_id);
                    loadedFullChats.add(chat_id);

                    putUsers(res.users, false);
                    putChats(res.chats, false);
                    if (res.full_chat.stickerset != null) {
                        DataQuery.getInstance(currentAccount).getGroupStickerSetById(res.full_chat.stickerset);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoaded, res.full_chat, classGuid, false, null);
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    checkChannelError(error.text, chat_id);
                    loadingFullChats.remove((Integer) chat_id);
                });
            }
        });
        if (classGuid != 0) {
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
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
        int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.TL_userFull userFull = (TLRPC.TL_userFull) response;
                    applyDialogNotificationsSettings(user.id, userFull.notify_settings);
                    if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                        DataQuery.getInstance(currentAccount).putBotInfo(userFull.bot_info);
                    }
                    int index = blockedUsers.indexOfKey(user.id);
                    if (userFull.blocked) {
                        if (index < 0) {
                            SparseIntArray ids = new SparseIntArray();
                            ids.put(user.id, 1);
                            MessagesStorage.getInstance(currentAccount).putBlockedUsers(ids, false);

                            blockedUsers.put(user.id, 1);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.blockedUsersDidLoaded);
                        }
                    } else {
                        if (index >= 0) {
                            MessagesStorage.getInstance(currentAccount).deleteBlockedUser(user.id);

                            blockedUsers.removeAt(index);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.blockedUsersDidLoaded);
                        }
                    }
                    fullUsers.put(user.id, userFull);
                    loadingFullUsers.remove((Integer) user.id);
                    loadedFullUsers.add(user.id);
                    String names = user.first_name + user.last_name + user.username;
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(userFull.user);
                    putUsers(users, false);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true);
                    if (names != null && !names.equals(userFull.user.first_name + userFull.user.last_name + userFull.user.username)) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_NAME);
                    }
                    if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botInfoDidLoaded, userFull.bot_info, classGuid);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.userInfoDidLoaded, user.id, userFull);
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> loadingFullUsers.remove((Integer) user.id));
            }
        });
        ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
    }

    private void reloadMessages(final ArrayList<Integer> mids, final long dialog_id) {
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
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
                    inboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
                    dialogs_read_inbox_max.put(dialog_id, inboxValue);
                }

                Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                if (outboxValue == null) {
                    outboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, dialog_id);
                    dialogs_read_outbox_max.put(dialog_id, outboxValue);
                }

                final ArrayList<MessageObject> objects = new ArrayList<>();
                for (int a = 0; a < messagesRes.messages.size(); a++) {
                    TLRPC.Message message = messagesRes.messages.get(a);
                    if (chat != null && chat.megagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }
                    message.dialog_id = dialog_id;
                    message.unread = (message.out ? outboxValue : inboxValue) < message.id;
                    objects.add(new MessageObject(currentAccount, message, usersLocal, chatsLocal, true));
                }

                ImageLoader.saveMessagesThumbs(messagesRes.messages);
                MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, dialog_id, -1, 0, false);

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
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                                break;
                            }
                        }
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, objects);
                });
            }
        });
    }

    public void hideReportSpam(final long dialogId, TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (currentUser == null && currentChat == null) {
            return;
        }
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        editor.putInt("spam3_" + dialogId, 1);
        editor.commit();
        if ((int) dialogId != 0) {
            TLRPC.TL_messages_hideReportSpam req = new TLRPC.TL_messages_hideReportSpam();
            if (currentUser != null) {
                req.peer = getInputPeer(currentUser.id);
            } else if (currentChat != null) {
                req.peer = getInputPeer(-currentChat.id);
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            });
        }
    }

    public void reportSpam(final long dialogId, TLRPC.User currentUser, TLRPC.Chat currentChat, TLRPC.EncryptedChat currentEncryptedChat) {
        if (currentUser == null && currentChat == null && currentEncryptedChat == null) {
            return;
        }
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        editor.putInt("spam3_" + dialogId, 1);
        editor.commit();
        if ((int) dialogId == 0) {
            if (currentEncryptedChat == null || currentEncryptedChat.access_hash == 0) {
                return;
            }
            TLRPC.TL_messages_reportEncryptedSpam req = new TLRPC.TL_messages_reportEncryptedSpam();
            req.peer = new TLRPC.TL_inputEncryptedChat();
            req.peer.chat_id = currentEncryptedChat.id;
            req.peer.access_hash = currentEncryptedChat.access_hash;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } else {
            TLRPC.TL_messages_reportSpam req = new TLRPC.TL_messages_reportSpam();
            if (currentChat != null) {
                req.peer = getInputPeer(-currentChat.id);
            } else if (currentUser != null) {
                req.peer = getInputPeer(currentUser.id);
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
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
        if (notificationsPreferences.getInt("spam3_" + dialogId, 0) == 1) {
            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("spam button already hidden for " + dialogId);
            }
            return;
        }
        boolean hidden = notificationsPreferences.getBoolean("spam_" + dialogId, false);
        if (hidden) {
            TLRPC.TL_messages_hideReportSpam req = new TLRPC.TL_messages_hideReportSpam();
            if (currentUser != null) {
                req.peer = getInputPeer(currentUser.id);
            } else if (currentChat != null) {
                req.peer = getInputPeer(-currentChat.id);
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                loadingPeerSettings.remove(dialogId);
                SharedPreferences.Editor editor = notificationsPreferences.edit();
                editor.remove("spam_" + dialogId);
                editor.putInt("spam3_" + dialogId, 1);
                editor.commit();
            }));
            return;
        }
        TLRPC.TL_messages_getPeerSettings req = new TLRPC.TL_messages_getPeerSettings();
        if (currentUser != null) {
            req.peer = getInputPeer(currentUser.id);
        } else if (currentChat != null) {
            req.peer = getInputPeer(-currentChat.id);
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            loadingPeerSettings.remove(dialogId);
            if (response != null) {
                TLRPC.TL_peerSettings res = (TLRPC.TL_peerSettings) response;
                SharedPreferences.Editor editor = notificationsPreferences.edit();
                if (!res.report_spam) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("don't show spam button for " + dialogId);
                    }
                    editor.putInt("spam3_" + dialogId, 1);
                    editor.commit();
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("show spam button for " + dialogId);
                    }
                    editor.putInt("spam3_" + dialogId, 2);
                    editor.commit();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.peerSettingsDidLoaded, dialogId);
                }
            }
        }));
    }

    protected void processNewChannelDifferenceParams(int pts, int pts_count, int channelId) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("processNewChannelDifferenceParams pts = " + pts + " pts_count = " + pts_count + " channeldId = " + channelId);
        }
        int channelPts = channelsPts.get(channelId);
        if (channelPts == 0) {
            channelPts = MessagesStorage.getInstance(currentAccount).getChannelPtsSync(channelId);
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
            MessagesStorage.getInstance(currentAccount).saveChannelPts(channelId, pts);
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
            if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() + pts_count == pts) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("APPLY PTS");
                }
                MessagesStorage.getInstance(currentAccount).setLastPtsValue(pts);
                MessagesStorage.getInstance(currentAccount).saveDiffParams(MessagesStorage.getInstance(currentAccount).getLastSeqValue(), MessagesStorage.getInstance(currentAccount).getLastPtsValue(), MessagesStorage.getInstance(currentAccount).getLastDateValue(), MessagesStorage.getInstance(currentAccount).getLastQtsValue());
            } else if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() != pts) {
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
            if (MessagesStorage.getInstance(currentAccount).getLastSeqValue() + 1 == seq) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("APPLY SEQ");
                }
                MessagesStorage.getInstance(currentAccount).setLastSeqValue(seq);
                if (date != -1) {
                    MessagesStorage.getInstance(currentAccount).setLastDateValue(date);
                }
                MessagesStorage.getInstance(currentAccount).saveDiffParams(MessagesStorage.getInstance(currentAccount).getLastSeqValue(), MessagesStorage.getInstance(currentAccount).getLastPtsValue(), MessagesStorage.getInstance(currentAccount).getLastDateValue(), MessagesStorage.getInstance(currentAccount).getLastQtsValue());
            } else if (MessagesStorage.getInstance(currentAccount).getLastSeqValue() != seq) {
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
        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didCreatedNewDeleteTask, mids));
    }

    public void getNewDeleteTask(final ArrayList<Integer> oldTask, final int channelId) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingNewDeleteTask = true;
            MessagesStorage.getInstance(currentAccount).getNewTask(oldTask, channelId);
        });
    }

    private boolean checkDeletingTask(boolean runnable) {
        int currentServerTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();

        if (currentDeletingTaskMids != null && (runnable || currentDeletingTaskTime != 0 && currentDeletingTaskTime <= currentServerTime)) {
            currentDeletingTaskTime = 0;
            if (currentDeleteTaskRunnable != null && !runnable) {
                Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            }
            currentDeleteTaskRunnable = null;
            final ArrayList<Integer> mids = new ArrayList<>(currentDeletingTaskMids);
            AndroidUtilities.runOnUIThread(() -> {
                if (!mids.isEmpty() && mids.get(0) > 0) {
                    MessagesStorage.getInstance(currentAccount).emptyMessagesMedia(mids);
                } else {
                    deleteMessages(mids, null, null, 0, false);
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
                    int currentServerTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
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
            MessagesStorage.getInstance(currentAccount).getDialogPhotos(did, count, max_id, classGuid);
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
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.photos_Photos res = (TLRPC.photos_Photos) response;
                        processLoadedUserPhotos(res, did, count, max_id, false, classGuid);
                    }
                });
                ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
            } else if (did < 0) {
                TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.filter = new TLRPC.TL_inputMessagesFilterChatPhotos();
                req.limit = count;
                req.offset_id = (int) max_id;
                req.q = "";
                req.peer = getInputPeer(did);
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
                ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
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
            DataQuery.getInstance(currentAccount).removeInline(user_id);
        } else {
            DataQuery.getInstance(currentAccount).removePeer(user_id);
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.blockedUsersDidLoaded);
        TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
        req.id = getInputUser(user);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                SparseIntArray ids = new SparseIntArray();
                ids.put(user.id, 1);
                MessagesStorage.getInstance(currentAccount).putBlockedUsers(ids, false);
            }
        });
    }

    public void setUserBannedRole(final int chatId, TLRPC.User user, TLRPC.TL_channelBannedRights rights, final boolean isMegagroup, final BaseFragment parentFragment) {
        if (user == null || rights == null) {
            return;
        }
        final TLRPC.TL_channels_editBanned req = new TLRPC.TL_channels_editBanned();
        req.channel = getInputChannel(chatId);
        req.user_id = getInputUser(user);
        req.banned_rights = rights;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, parentFragment, req, !isMegagroup));
            }
        });
    }

    public void setUserAdminRole(final int chatId, TLRPC.User user, TLRPC.TL_channelAdminRights rights, final boolean isMegagroup, final BaseFragment parentFragment) {
        if (user == null || rights == null) {
            return;
        }
        final TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
        req.channel = getInputChannel(chatId);
        req.user_id = getInputUser(user);
        req.admin_rights = rights;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> loadFullChat(chatId, 0, true), 1000);
            } else {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, parentFragment, req, !isMegagroup));
            }
        });
    }

    public void unblockUser(int user_id) {
        TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
        final TLRPC.User user = getUser(user_id);
        if (user == null) {
            return;
        }
        blockedUsers.delete(user.id);
        req.id = getInputUser(user);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.blockedUsersDidLoaded);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> MessagesStorage.getInstance(currentAccount).deleteBlockedUser(user.id));
    }

    public void getBlockedUsers(boolean cache) {
        if (!UserConfig.getInstance(currentAccount).isClientActivated() || loadingBlockedUsers) {
            return;
        }
        loadingBlockedUsers = true;
        if (cache) {
            MessagesStorage.getInstance(currentAccount).getBlockedUsers();
        } else {
            TLRPC.TL_contacts_getBlocked req = new TLRPC.TL_contacts_getBlocked();
            req.offset = 0;
            req.limit = 200;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                SparseIntArray blocked = new SparseIntArray();
                ArrayList<TLRPC.User> users = null;
                if (error == null) {
                    final TLRPC.contacts_Blocked res = (TLRPC.contacts_Blocked) response;
                    for (TLRPC.TL_contactBlocked contactBlocked : res.blocked) {
                        blocked.put(contactBlocked.user_id, 1);
                    }
                    users = res.users;
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, null, true, true);
                    MessagesStorage.getInstance(currentAccount).putBlockedUsers(blocked, true);
                }
                processLoadedBlockedUsers(blocked, users, false);
            });
        }
    }

    public void processLoadedBlockedUsers(final SparseIntArray ids, final ArrayList<TLRPC.User> users, final boolean cache) {
        AndroidUtilities.runOnUIThread(() -> {
            if (users != null) {
                putUsers(users, cache);
            }
            loadingBlockedUsers = false;
            if (ids.size() == 0 && cache && !UserConfig.getInstance(currentAccount).blockedUsersLoaded) {
                getBlockedUsers(false);
                return;
            } else if (!cache) {
                UserConfig.getInstance(currentAccount).blockedUsersLoaded = true;
                UserConfig.getInstance(currentAccount).saveConfig(false);
            }
            blockedUsers = ids;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.blockedUsersDidLoaded);
        });
    }

    public void deleteUserPhoto(TLRPC.InputPhoto photo) {
        if (photo == null) {
            TLRPC.TL_photos_updateProfilePhoto req = new TLRPC.TL_photos_updateProfilePhoto();
            req.id = new TLRPC.TL_inputPhotoEmpty();
            UserConfig.getInstance(currentAccount).getCurrentUser().photo = new TLRPC.TL_userProfilePhotoEmpty();
            TLRPC.User user = getUser(UserConfig.getInstance(currentAccount).getClientUserId());
            if (user == null) {
                user = UserConfig.getInstance(currentAccount).getCurrentUser();
            }
            if (user == null) {
                return;
            }
            user.photo = UserConfig.getInstance(currentAccount).getCurrentUser().photo;
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.User user1 = getUser(UserConfig.getInstance(currentAccount).getClientUserId());
                    if (user1 == null) {
                        user1 = UserConfig.getInstance(currentAccount).getCurrentUser();
                        putUser(user1, false);
                    } else {
                        UserConfig.getInstance(currentAccount).setCurrentUser(user1);
                    }
                    if (user1 == null) {
                        return;
                    }
                    MessagesStorage.getInstance(currentAccount).clearUserPhotos(user1.id);
                    ArrayList<TLRPC.User> users = new ArrayList<>();
                    users.add(user1);
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(users, null, false, true);
                    user1.photo = (TLRPC.UserProfilePhoto) response;
                    AndroidUtilities.runOnUIThread(() -> {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mainUserInfoChanged);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL);
                        UserConfig.getInstance(currentAccount).saveConfig(true);
                    });
                }
            });
        } else {
            TLRPC.TL_photos_deletePhotos req = new TLRPC.TL_photos_deletePhotos();
            req.id.add(photo);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            });
        }
    }

    public void processLoadedUserPhotos(final TLRPC.photos_Photos res, final int did, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (!fromCache) {
            MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, null, true, true);
            MessagesStorage.getInstance(currentAccount).putDialogPhotos(did, res);
        } else if (res == null || res.photos.isEmpty()) {
            loadDialogPhotos(did, count, max_id, false, classGuid);
            return;
        }
        AndroidUtilities.runOnUIThread(() -> {
            putUsers(res.users, fromCache);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogPhotosLoaded, did, count, fromCache, classGuid, res.photos);
        });
    }

    public void uploadAndApplyUserAvatar(TLRPC.PhotoSize bigPhoto) {
        if (bigPhoto != null) {
            uploadingAvatar = FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
            FileLoader.getInstance(currentAccount).uploadFile(uploadingAvatar, false, true, ConnectionsManager.FileTypePhoto);
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

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, final int channelId, boolean forAll) {
        deleteMessages(messages, randoms, encryptedChat, channelId, forAll, 0, null);
    }

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, final int channelId, boolean forAll, long taskId, TLObject taskRequest) {
        if ((messages == null || messages.isEmpty()) && taskRequest == null) {
            return;
        }
        ArrayList<Integer> toSend = null;
        if (taskId == 0) {
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
            toSend = new ArrayList<>();
            for (int a = 0; a < messages.size(); a++) {
                Integer mid = messages.get(a);
                if (mid > 0) {
                    toSend.add(mid);
                }
            }
            MessagesStorage.getInstance(currentAccount).markMessagesAsDeleted(messages, true, channelId);
            MessagesStorage.getInstance(currentAccount).updateDialogsWithDeletedMessages(messages, null, true, channelId);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDeleted, messages, channelId);
        }

        final long newTaskId;
        if (channelId != 0) {
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
                newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewChannelDifferenceParams(res.pts, res.pts_count, channelId);
                }
                if (newTaskId != 0) {
                    MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
                }
            });
        } else {
            if (randoms != null && encryptedChat != null && !randoms.isEmpty()) {
                SecretChatHelper.getInstance(currentAccount).sendMessagesDeleteMessage(encryptedChat, randoms, null);
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
                newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
                if (newTaskId != 0) {
                    MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
                }
            });
        }
    }

    public void pinChannelMessage(TLRPC.Chat chat, int id, boolean notify) {
        TLRPC.TL_channels_updatePinnedMessage req = new TLRPC.TL_channels_updatePinnedMessage();
        req.channel = getInputChannel(chat);
        req.id = id;
        req.silent = !notify;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
            }
        });
    }

    public void deleteUserChannelHistory(final TLRPC.Chat chat, final TLRPC.User user, int offset) {
        if (offset == 0) {
            MessagesStorage.getInstance(currentAccount).deleteUserChannelHistory(chat.id, user.id);
        }
        TLRPC.TL_channels_deleteUserHistory req = new TLRPC.TL_channels_deleteUserHistory();
        req.channel = getInputChannel(chat);
        req.user_id = getInputUser(user);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                if (res.offset > 0) {
                    deleteUserChannelHistory(chat, user, res.offset);
                }
                processNewChannelDifferenceParams(res.pts, res.pts_count, chat.id);
            }
        });
    }

    public void deleteDialog(final long did, final int onlyHistory) {
        deleteDialog(did, true, onlyHistory, 0);
    }

    private void deleteDialog(final long did, final boolean first, final int onlyHistory, final int max_id) {
        int lower_part = (int) did;
        int high_id = (int) (did >> 32);
        int max_id_delete = max_id;

        if (onlyHistory == 2) {
            MessagesStorage.getInstance(currentAccount).deleteDialog(did, onlyHistory);
            return;
        }
        if (onlyHistory == 0 || onlyHistory == 3) {
            DataQuery.getInstance(currentAccount).uninstallShortcut(did);
        }

        if (first) {
            boolean isProxyDialog = false;
            MessagesStorage.getInstance(currentAccount).deleteDialog(did, onlyHistory);
            TLRPC.TL_dialog dialog = dialogs_dict.get(did);
            if (dialog != null) {
                if (max_id_delete == 0) {
                    max_id_delete = Math.max(0, dialog.top_message);
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
                        dialogs.remove(dialog);
                        if (dialogsServerOnly.remove(dialog) && DialogObject.isChannel(dialog)) {
                            Utilities.stageQueue.postRunnable(() -> {
                                channelsPts.delete(-(int) did);
                                shortPollChannels.delete(-(int) did);
                                needShortPollChannels.delete(-(int) did);
                            });
                        }
                        dialogsGroupsOnly.remove(dialog);
                        dialogs_dict.remove(did);
                        dialogs_read_inbox_max.remove(did);
                        dialogs_read_outbox_max.remove(did);
                        nextDialogsCacheOffset--;
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
                        message.out = UserConfig.getInstance(currentAccount).getClientUserId() == did;
                        message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
                        message.flags |= 256;
                        message.action = new TLRPC.TL_messageActionHistoryClear();
                        message.date = dialog.last_message_date;
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
                        updateInterfaceWithMessages(did, objArr);
                        MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0);
                    } else {
                        dialog.top_message = 0;
                    }
                }
            }
            if (isProxyDialog) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload, true);
            } else {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, false);
            }
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(did)));
        }

        if (high_id == 1 || onlyHistory == 3) {
            return;
        }

        if (lower_part != 0) {
            TLRPC.InputPeer peer = getInputPeer(lower_part);
            if (peer == null) {
                return;
            }
            if (peer instanceof TLRPC.TL_inputPeerChannel) {
                if (onlyHistory == 0) {
                    return;
                }
                TLRPC.TL_channels_deleteHistory req = new TLRPC.TL_channels_deleteHistory();
                req.channel = new TLRPC.TL_inputChannel();
                req.channel.channel_id = peer.channel_id;
                req.channel.access_hash = peer.access_hash;
                req.max_id = max_id_delete > 0 ? max_id_delete : Integer.MAX_VALUE;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                }, ConnectionsManager.RequestFlagInvokeAfter);
            } else {
                TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
                req.peer = peer;
                req.max_id = (onlyHistory == 0 ? Integer.MAX_VALUE : max_id_delete);
                req.just_clear = onlyHistory != 0;
                final int max_id_delete_final = max_id_delete;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                        if (res.offset > 0) {
                            deleteDialog(did, false, onlyHistory, max_id_delete_final);
                        }
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    }
                }, ConnectionsManager.RequestFlagInvokeAfter);
            }
        } else {
            if (onlyHistory == 1) {
                SecretChatHelper.getInstance(currentAccount).sendClearHistoryMessage(getEncryptedChat(high_id), null);
            } else {
                SecretChatHelper.getInstance(currentAccount).declineSecretChat(high_id);
            }
        }
    }

    public void saveGif(TLRPC.Document document) {
        TLRPC.TL_messages_saveGif req = new TLRPC.TL_messages_saveGif();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.unsave = false;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }

    public void saveRecentSticker(TLRPC.Document document, boolean asMask) {
        TLRPC.TL_messages_saveRecentSticker req = new TLRPC.TL_messages_saveRecentSticker();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.unsave = false;
        req.attached = asMask;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (error == null) {
                TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                putUsers(res.users, false);
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, null, true, true);
                MessagesStorage.getInstance(currentAccount).updateChannelUsers(chat_id, res.participants);
                loadedFullParticipants.add(chat_id);
            }
            loadingFullParticipants.remove(chat_id);
        }));
    }

    public void loadChatInfo(final int chat_id, CountDownLatch countDownLatch, boolean force) {
        MessagesStorage.getInstance(currentAccount).loadChatInfo(chat_id, countDownLatch, force, false);
    }

    public void processChatInfo(int chat_id, final TLRPC.ChatFull info, final ArrayList<TLRPC.User> usersArr, final boolean fromCache, boolean force, final boolean byChannelUsers, final MessageObject pinnedMessageObject) {
        if (fromCache && chat_id > 0 && !byChannelUsers) {
            loadFullChat(chat_id, 0, force);
        }
        if (info != null) {
            AndroidUtilities.runOnUIThread(() -> {
                putUsers(usersArr, fromCache);
                if (info.stickerset != null) {
                    DataQuery.getInstance(currentAccount).getGroupStickerSetById(info.stickerset);
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, byChannelUsers, pinnedMessageObject);
            });
        }
    }

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();

        checkDeletingTask(false);
        checkReadTasks();

        if (UserConfig.getInstance(currentAccount).isClientActivated()) {
            if (ConnectionsManager.getInstance(currentAccount).getPauseTime() == 0 && ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePausedStageQueue) {
                if (ApplicationLoader.mainInterfacePausedStageQueueTime != 0 && Math.abs(ApplicationLoader.mainInterfacePausedStageQueueTime - System.currentTimeMillis()) > 1000) {
                    if (statusSettingState != 1 && (lastStatusUpdateTime == 0 || Math.abs(System.currentTimeMillis() - lastStatusUpdateTime) >= 55000 || offlineSent)) {
                        statusSettingState = 1;

                        if (statusRequest != 0) {
                            ConnectionsManager.getInstance(currentAccount).cancelRequest(statusRequest, true);
                        }

                        TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                        req.offline = false;
                        statusRequest = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
            } else if (statusSettingState != 2 && !offlineSent && Math.abs(System.currentTimeMillis() - ConnectionsManager.getInstance(currentAccount).getPauseTime()) >= 2000) {
                statusSettingState = 2;
                if (statusRequest != 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(statusRequest, true);
                }
                TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                req.offline = true;
                statusRequest = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
        if (channelViewsToSend.size() != 0 && Math.abs(System.currentTimeMillis() - lastViewsCheckTime) >= 5000) {
            lastViewsCheckTime = System.currentTimeMillis();
            for (int a = 0; a < channelViewsToSend.size(); a++) {
                final int key = channelViewsToSend.keyAt(a);
                final TLRPC.TL_messages_getMessagesViews req = new TLRPC.TL_messages_getMessagesViews();
                req.peer = getInputPeer(key);
                req.id = channelViewsToSend.valueAt(a);
                req.increment = a == 0;
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (error == null) {
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
                        MessagesStorage.getInstance(currentAccount).putChannelViews(channelViews, req.peer instanceof TLRPC.TL_inputPeerChannel);
                        AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdatedMessagesViews, channelViews));
                    }
                });
            }
            channelViewsToSend.clear();
        }
        if (!onlinePrivacy.isEmpty()) {
            ArrayList<Integer> toRemove = null;
            int currentServerTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
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
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS));
            }
        }
        if (shortPollChannels.size() != 0) {
            for (int a = 0; a < shortPollChannels.size(); a++) {
                int key = shortPollChannels.keyAt(a);
                int timeout = shortPollChannels.valueAt(a);
                if (timeout < System.currentTimeMillis() / 1000) {
                    shortPollChannels.delete(key);
                    if (needShortPollChannels.indexOfKey(key) >= 0) {
                        getChannelDifference(key);
                    }
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
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT));
            }
        }
        if (Theme.selectedAutoNightType == Theme.AUTO_NIGHT_TYPE_SCHEDULED && Math.abs(currentTime - lastThemeCheckTime) >= 60) {
            AndroidUtilities.runOnUIThread(themeCheckRunnable);
            lastThemeCheckTime = currentTime;
        }
        if (UserConfig.getInstance(currentAccount).savedPasswordHash != null && Math.abs(currentTime - lastPasswordCheckTime) >= 60) {
            AndroidUtilities.runOnUIThread(passwordCheckRunnable);
            lastPasswordCheckTime = currentTime;
        }
        if (lastPushRegisterSendTime != 0 && Math.abs(SystemClock.elapsedRealtime() - lastPushRegisterSendTime) >= 3 * 60 * 60 * 1000) {
            GcmInstanceIDListenerService.sendRegistrationToServer(SharedConfig.pushString);
        }
        LocationController.getInstance(currentAccount).update();
        checkProxyInfoInternal(false);
        checkTosUpdate();
    }

    private void checkTosUpdate() {
        if (nextTosCheckTime > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || checkingTosUpdate || !UserConfig.getInstance(currentAccount).isClientActivated()) {
            return;
        }
        checkingTosUpdate = true;
        TLRPC.TL_help_getTermsOfServiceUpdate req = new TLRPC.TL_help_getTermsOfServiceUpdate();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            checkingTosUpdate = false;
            if (response instanceof TLRPC.TL_help_termsOfServiceUpdateEmpty) {
                TLRPC.TL_help_termsOfServiceUpdateEmpty res = (TLRPC.TL_help_termsOfServiceUpdateEmpty) response;
                nextTosCheckTime = res.expires;
            } else if (response instanceof TLRPC.TL_help_termsOfServiceUpdate) {
                final TLRPC.TL_help_termsOfServiceUpdate res = (TLRPC.TL_help_termsOfServiceUpdate) response;
                nextTosCheckTime = res.expires;
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 4, res.terms_of_service));
            } else {
                nextTosCheckTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60;
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
        if (!reset && nextProxyInfoCheckTime > ConnectionsManager.getInstance(currentAccount).getCurrentTime() || checkingProxyInfo) {
            return;
        }
        SharedPreferences preferences = getGlobalMainSettings();
        boolean enabled = preferences.getBoolean("proxy_enabled", false);
        String proxyAddress = preferences.getString("proxy_ip", "");
        String proxySecret = preferences.getString("proxy_secret", "");
        if (enabled && !TextUtils.isEmpty(proxyAddress) && !TextUtils.isEmpty(proxySecret)) {
            checkingProxyInfo = true;
            TLRPC.TL_help_getProxyData req = new TLRPC.TL_help_getProxyData();
            checkingProxyInfoRequestId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (checkingProxyInfoRequestId == 0) {
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
                    getGlobalMainSettings().edit().putLong("proxy_dialog", proxyDialogId).commit();
                    nextProxyInfoCheckTime = res.expires;
                    if (!noDialog) {
                        AndroidUtilities.runOnUIThread(() -> {
                            proxyDialog = dialogs_dict.get(did);

                            if (proxyDialog != null) {
                                checkingProxyInfo = false;
                                sortDialogs(null);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload, true);
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
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req1, (response1, error1) -> {
                                    if (checkingProxyInfoRequestId == 0) {
                                        return;
                                    }
                                    checkingProxyInfoRequestId = 0;
                                    final TLRPC.TL_messages_peerDialogs res2 = (TLRPC.TL_messages_peerDialogs) response1;
                                    if (res2 != null && !res2.dialogs.isEmpty()) {
                                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                                        TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                                        dialogs.chats = res2.chats;
                                        dialogs.users = res2.users;
                                        dialogs.dialogs = res2.dialogs;
                                        dialogs.messages = res2.messages;
                                        MessagesStorage.getInstance(currentAccount).putDialogs(dialogs, 2);
                                        AndroidUtilities.runOnUIThread(() -> {
                                            putUsers(res.users, false);
                                            putChats(res.chats, false);
                                            putUsers(res2.users, false);
                                            putChats(res2.chats, false);

                                            proxyDialog = res2.dialogs.get(0);
                                            proxyDialog.id = did;
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
                                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload, true);
                                        });
                                    } else {
                                        AndroidUtilities.runOnUIThread(() -> {
                                            if (proxyDialog != null) {
                                                if (proxyDialog.id < 0) {
                                                    TLRPC.Chat chat = getChat(-(int) proxyDialog.id);
                                                    if (chat == null || chat.left || chat.kicked || chat.restricted) {
                                                        dialogs_dict.remove(proxyDialog.id);
                                                        dialogs.remove(proxyDialog);
                                                    }
                                                }
                                                proxyDialog = null;
                                                sortDialogs(null);
                                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                                            }
                                        });
                                    }
                                    checkingProxyInfo = false;
                                });
                            }
                        });
                    }
                } else {
                    nextProxyInfoCheckTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60;
                    noDialog = true;
                }
                if (noDialog) {
                    proxyDialogId = 0;
                    getGlobalMainSettings().edit().putLong("proxy_dialog", proxyDialogId).commit();
                    checkingProxyInfoRequestId = 0;
                    checkingProxyInfo = false;
                    AndroidUtilities.runOnUIThread(() -> {
                        if (proxyDialog != null) {
                            if (proxyDialog.id < 0) {
                                TLRPC.Chat chat = getChat(-(int) proxyDialog.id);
                                if (chat == null || chat.left || chat.kicked || chat.restricted) {
                                    dialogs_dict.remove(proxyDialog.id);
                                    dialogs.remove(proxyDialog);
                                }
                            }
                            proxyDialog = null;
                            sortDialogs(null);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                    });
                }
            });
        } else {
            proxyDialogId = 0;
            getGlobalMainSettings().edit().putLong("proxy_dialog", proxyDialogId).commit();
            nextProxyInfoCheckTime = ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60;
            checkingProxyInfo = false;
            if (checkingProxyInfoRequestId != 0) {
                ConnectionsManager.getInstance(currentAccount).cancelRequest(checkingProxyInfoRequestId, true);
                checkingProxyInfoRequestId = 0;
            }
            AndroidUtilities.runOnUIThread(() -> {
                if (proxyDialog != null) {
                    if (proxyDialog.id < 0) {
                        TLRPC.Chat chat = getChat(-(int) proxyDialog.id);
                        if (chat == null || chat.left || chat.kicked || chat.restricted) {
                            dialogs_dict.remove(proxyDialog.id);
                            dialogs.remove(proxyDialog);
                        }
                    }
                    proxyDialog = null;
                    sortDialogs(null);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                }
            });
        }
    }

    public boolean isProxyDialog(long did) {
        return proxyDialog != null && proxyDialog.id == did && isLeftProxyChannel;
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

        ArrayList<Long> keys = new ArrayList<>(printingUsers.keySet());
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
                            newPrintingStrings.put(key, String.format(plural, label.toString(), arr.size() - 2));
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
            if (high_id == 1) {
                return;
            }

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
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                LongSparseArray<Boolean> typings1 = sendingTypings.get(action);
                if (typings1 != null) {
                    typings1.remove(dialog_id);
                }
            }), ConnectionsManager.RequestFlagFailOnServerErrors);
            if (classGuid != 0) {
                ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
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
                int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    LongSparseArray<Boolean> typings12 = sendingTypings.get(action);
                    if (typings12 != null) {
                        typings12.remove(dialog_id);
                    }
                }), ConnectionsManager.RequestFlagFailOnServerErrors);
                if (classGuid != 0) {
                    ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
                }
            }
        }
    }

    public void loadMessages(final long dialog_id, final int count, final int max_id, final int offset_date, boolean fromCache, int midDate, final int classGuid, final int load_type, final int last_message_id, final boolean isChannel, final int loadIndex) {
        loadMessages(dialog_id, count, max_id, offset_date, fromCache, midDate, classGuid, load_type, last_message_id, isChannel, loadIndex, 0, 0, 0, false, 0);
    }

    public void loadMessages(final long dialog_id, final int count, final int max_id, final int offset_date, final boolean fromCache, final int midDate, final int classGuid, final int load_type, final int last_message_id, final boolean isChannel, final int loadIndex, final int first_unread, final int unread_count, final int last_date, final boolean queryFromServer, final int mentionsCount) {
        loadMessagesInternal(dialog_id, count, max_id, offset_date, fromCache, midDate, classGuid, load_type, last_message_id, isChannel, loadIndex, first_unread, unread_count, last_date, queryFromServer, mentionsCount, true);
    }

    private void loadMessagesInternal(final long dialog_id, final int count, final int max_id, final int offset_date, final boolean fromCache, final int midDate, final int classGuid, final int load_type, final int last_message_id, final boolean isChannel, final int loadIndex, final int first_unread, final int unread_count, final int last_date, final boolean queryFromServer, final int mentionsCount, boolean loadDialog) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load messages in chat " + dialog_id + " count " + count + " max_id " + max_id + " cache " + fromCache + " mindate = " + midDate + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " index " + loadIndex + " firstUnread " + first_unread + " unread_count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        }
        int lower_part = (int) dialog_id;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance(currentAccount).getMessages(dialog_id, count, max_id, offset_date, midDate, classGuid, load_type, isChannel, loadIndex);
        } else {
            if (loadDialog && (load_type == 3 || load_type == 2) && last_message_id == 0) {
                TLRPC.TL_messages_getPeerDialogs req = new TLRPC.TL_messages_getPeerDialogs();
                TLRPC.InputPeer inputPeer = getInputPeer((int) dialog_id);
                TLRPC.TL_inputDialogPeer inputDialogPeer = new TLRPC.TL_inputDialogPeer();
                inputDialogPeer.peer = inputPeer;
                req.peers.add(inputDialogPeer);

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (response != null) {
                        TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                        if (!res.dialogs.isEmpty()) {
                            TLRPC.TL_dialog dialog = res.dialogs.get(0);

                            if (dialog.top_message != 0) {
                                TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                                dialogs.chats = res.chats;
                                dialogs.users = res.users;
                                dialogs.dialogs = res.dialogs;
                                dialogs.messages = res.messages;
                                MessagesStorage.getInstance(currentAccount).putDialogs(dialogs, 0);
                            }

                            loadMessagesInternal(dialog_id, count, max_id, offset_date, false, midDate, classGuid, load_type, dialog.top_message, isChannel, loadIndex, first_unread, dialog.unread_count, last_date, queryFromServer, dialog.unread_mentions_count, false);
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
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (response != null) {
                    final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
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
                    processLoadedMessages(res, dialog_id, count, mid, offset_date, false, classGuid, first_unread, last_message_id, unread_count, last_date, load_type, isChannel, false, loadIndex, queryFromServer, mentionsCount);
                }
            });
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }
    }

    public void reloadWebPages(final long dialog_id, HashMap<String, ArrayList<MessageObject>> webpagesToReload) {
        for (HashMap.Entry<String, ArrayList<MessageObject>> entry : webpagesToReload.entrySet()) {
            final String url = entry.getKey();
            final ArrayList<MessageObject> messages = entry.getValue();
            ArrayList<MessageObject> arrayList = reloadingWebpages.get(url);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                reloadingWebpages.put(url, arrayList);
            }
            arrayList.addAll(messages);
            TLRPC.TL_messages_getWebPagePreview req = new TLRPC.TL_messages_getWebPagePreview();
            req.message = url;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                ArrayList<MessageObject> arrayList1 = reloadingWebpages.remove(url);
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
                        reloadingWebpagesPending.put(media.webpage.id, arrayList1);
                    }
                }
                if (!messagesRes.messages.isEmpty()) {
                    MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, dialog_id, -2, 0, false);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList1);
                }
            }));
        }
    }

    public void processLoadedMessages(final TLRPC.messages_Messages messagesRes, final long dialog_id, final int count, final int max_id, final int offset_date, final boolean isCache, final int classGuid,
                                      final int first_unread, final int last_message_id, final int unread_count, final int last_date, final int load_type, final boolean isChannel, final boolean isEnd, final int loadIndex, final boolean queryFromServer, final int mentionsCount) {
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("processLoadedMessages size " + messagesRes.messages.size() + " in chat " + dialog_id + " count " + count + " max_id " + max_id + " cache " + isCache + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " isChannel " + isChannel + " index " + loadIndex + " firstUnread " + first_unread + " unread_count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        }
        Utilities.stageQueue.postRunnable(() -> {
            boolean createDialog = false;
            boolean isMegagroup = false;
            if (messagesRes instanceof TLRPC.TL_messages_channelMessages) {
                int channelId = -(int) dialog_id;
                int channelPts = channelsPts.get(channelId);
                if (channelPts == 0) {
                    channelPts = MessagesStorage.getInstance(currentAccount).getChannelPtsSync(channelId);
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
            if (high_id != 1 && lower_id != 0 && isCache && messagesRes.messages.size() == 0) {
                AndroidUtilities.runOnUIThread(() -> loadMessages(dialog_id, count, load_type == 2 && queryFromServer ? first_unread : max_id, offset_date, false, 0, classGuid, load_type, last_message_id, isChannel, loadIndex, first_unread, unread_count, last_date, queryFromServer, mentionsCount));
                return;
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
                    inboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
                    dialogs_read_inbox_max.put(dialog_id, inboxValue);
                }

                Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                if (outboxValue == null) {
                    outboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, dialog_id);
                    dialogs_read_outbox_max.put(dialog_id, outboxValue);
                }

                for (int a = 0; a < size; a++) {
                    TLRPC.Message message = messagesRes.messages.get(a);
                    if (isMegagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }

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
                MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, dialog_id, load_type, max_id, createDialog);
            }
            final ArrayList<MessageObject> objects = new ArrayList<>();
            final ArrayList<Integer> messagesToReload = new ArrayList<>();
            final HashMap<String, ArrayList<MessageObject>> webpagesToReload = new HashMap<>();
            TLRPC.InputChannel inputChannel = null;
            for (int a = 0; a < size; a++) {
                TLRPC.Message message = messagesRes.messages.get(a);
                message.dialog_id = dialog_id;
                MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, true);
                objects.add(messageObject);
                if (isCache) {
                    if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                        if (message.media.bytes != null && (message.media.bytes.length == 0 || message.media.bytes.length == 1 && message.media.bytes[0] < TLRPC.LAYER)) {
                            messagesToReload.add(message.id);
                        }
                    } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                        if (message.media.webpage instanceof TLRPC.TL_webPagePending && message.media.webpage.date <= ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
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
                int first_unread_final = Integer.MAX_VALUE;
                if (queryFromServer && load_type == 2) {
                    for (int a = 0; a < messagesRes.messages.size(); a++) {
                        TLRPC.Message message = messagesRes.messages.get(a);
                        if (!message.out && message.id > first_unread && message.id < first_unread_final) {
                            first_unread_final = message.id;
                        }
                    }
                }
                if (first_unread_final == Integer.MAX_VALUE) {
                    first_unread_final = first_unread;
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDidLoaded, dialog_id, count, objects, isCache, first_unread_final, last_message_id, unread_count, last_date, load_type, isEnd, classGuid, loadIndex, max_id, mentionsCount);
                if (!messagesToReload.isEmpty()) {
                    reloadMessages(messagesToReload, dialog_id);
                }
                if (!webpagesToReload.isEmpty()) {
                    reloadWebPages(dialog_id, webpagesToReload);
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                AndroidUtilities.runOnUIThread(() -> {
                    /*installReferer = null;
                    mainPreferences.edit().remove("installReferer").commit();*/

                    TLRPC.TL_help_recentMeUrls res = (TLRPC.TL_help_recentMeUrls) response;
                    putUsers(res.users, false);
                    putChats(res.chats, false);
                    hintDialogs.clear();
                    hintDialogs.addAll(res.urls);

                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                });
            }
        });
    }

    public void loadDialogs(final int offset, final int count, boolean fromCache) {
        if (loadingDialogs || resetingDialogs) {
            return;
        }
        loadingDialogs = true;
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("load cacheOffset = " + offset + " count = " + count + " cache = " + fromCache);
        }
        if (fromCache) {
            MessagesStorage.getInstance(currentAccount).getDialogs(offset == 0 ? 0 : nextDialogsCacheOffset, count);
        } else {
            TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
            req.limit = count;
            req.exclude_pinned = true;
            if (UserConfig.getInstance(currentAccount).dialogsLoadOffsetId != -1) {
                if (UserConfig.getInstance(currentAccount).dialogsLoadOffsetId == Integer.MAX_VALUE) {
                    dialogsEndReached = true;
                    serverDialogsEndReached = true;
                    loadingDialogs = false;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                    return;
                }
                req.offset_id = UserConfig.getInstance(currentAccount).dialogsLoadOffsetId;
                req.offset_date = UserConfig.getInstance(currentAccount).dialogsLoadOffsetDate;
                if (req.offset_id == 0) {
                    req.offset_peer = new TLRPC.TL_inputPeerEmpty();
                } else {
                    if (UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId != 0) {
                        req.offset_peer = new TLRPC.TL_inputPeerChannel();
                        req.offset_peer.channel_id = UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId;
                    } else if (UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId != 0) {
                        req.offset_peer = new TLRPC.TL_inputPeerUser();
                        req.offset_peer.user_id = UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId;
                    } else {
                        req.offset_peer = new TLRPC.TL_inputPeerChat();
                        req.offset_peer.chat_id = UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId;
                    }
                    req.offset_peer.access_hash = UserConfig.getInstance(currentAccount).dialogsLoadOffsetAccess;
                }
            } else {
                boolean found = false;
                for (int a = dialogs.size() - 1; a >= 0; a--) {
                    TLRPC.TL_dialog dialog = dialogs.get(a);
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                    processLoadedDialogs(dialogsRes, null, 0, count, 0, false, false, false);
                }
            });
        }
    }

    public void loadGlobalNotificationsSettings() {
        if (loadingNotificationSettings != 0 || UserConfig.getInstance(currentAccount).notificationsSettingsLoaded) {
            return;
        }
        loadingNotificationSettings = 2;
        for (int a = 0; a < 2; a++) {
            TLRPC.TL_account_getNotifySettings req = new TLRPC.TL_account_getNotifySettings();
            if (a == 0) {
                req.peer = new TLRPC.TL_inputNotifyChats();
            } else if (a == 1) {
                req.peer = new TLRPC.TL_inputNotifyUsers();
            }
            final int type = a;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
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
                            editor.putBoolean("EnableGroup", notify_settings.mute_until < ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                        }
                    } else {
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
                            editor.putBoolean("EnableAll", notify_settings.mute_until < ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                        }
                    }
                    editor.commit();
                    if (loadingNotificationSettings == 0) {
                        UserConfig.getInstance(currentAccount).notificationsSettingsLoaded = true;
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                    }
                }
            }));
        }
    }

    public void forceResetDialogs() {
        resetDialogs(true, MessagesStorage.getInstance(currentAccount).getLastSeqValue(), MessagesStorage.getInstance(currentAccount).getLastPtsValue(), MessagesStorage.getInstance(currentAccount).getLastDateValue(), MessagesStorage.getInstance(currentAccount).getLastQtsValue());
    }

    private void resetDialogs(boolean query, final int seq, final int newPts, final int date, final int qts) {
        if (query) {
            if (resetingDialogs) {
                return;
            }
            resetingDialogs = true;
            TLRPC.TL_messages_getPinnedDialogs req = new TLRPC.TL_messages_getPinnedDialogs();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (response != null) {
                    resetDialogsPinned = (TLRPC.TL_messages_peerDialogs) response;
                    resetDialogs(false, seq, newPts, date, qts);
                }
            });
            TLRPC.TL_messages_getDialogs req2 = new TLRPC.TL_messages_getDialogs();
            req2.limit = 100;
            req2.exclude_pinned = true;
            req2.offset_peer = new TLRPC.TL_inputPeerEmpty();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> {
                if (error == null) {
                    resetDialogsAll = (TLRPC.messages_Dialogs) response;
                    resetDialogs(false, seq, newPts, date, qts);
                }
            });
        } else if (resetDialogsPinned != null && resetDialogsAll != null) {
            int messagesCount = resetDialogsAll.messages.size();
            int dialogsCount = resetDialogsAll.dialogs.size();
            resetDialogsAll.dialogs.addAll(resetDialogsPinned.dialogs);
            resetDialogsAll.messages.addAll(resetDialogsPinned.messages);
            resetDialogsAll.users.addAll(resetDialogsPinned.users);
            resetDialogsAll.chats.addAll(resetDialogsPinned.chats);

            final LongSparseArray<TLRPC.TL_dialog> new_dialogs_dict = new LongSparseArray<>();
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
                TLRPC.TL_dialog d = resetDialogsAll.dialogs.get(a);
                if (d.id == 0 && d.peer != null) {
                    if (d.peer.user_id != 0) {
                        d.id = d.peer.user_id;
                    } else if (d.peer.chat_id != 0) {
                        d.id = -d.peer.chat_id;
                    } else if (d.peer.channel_id != 0) {
                        d.id = -d.peer.channel_id;
                    }
                }
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
                        value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
                        read_max.put(message.dialog_id, value);
                    }
                    message.unread = value < message.id;
                }
            }

            MessagesStorage.getInstance(currentAccount).resetDialogs(resetDialogsAll, messagesCount, seq, newPts, date, qts, new_dialogs_dict, new_dialogMessage, lastMessage, dialogsCount);
            resetDialogsPinned = null;
            resetDialogsAll = null;
        }
    }

    protected void completeDialogsReset(final TLRPC.messages_Dialogs dialogsRes, final int messagesCount, final int seq, final int newPts, final int date, final int qts, final LongSparseArray<TLRPC.TL_dialog> new_dialogs_dict, final LongSparseArray<MessageObject> new_dialogMessage, final TLRPC.Message lastMessage) {
        Utilities.stageQueue.postRunnable(() -> {
            gettingDifference = false;
            MessagesStorage.getInstance(currentAccount).setLastPtsValue(newPts);
            MessagesStorage.getInstance(currentAccount).setLastDateValue(date);
            MessagesStorage.getInstance(currentAccount).setLastQtsValue(qts);
            getDifference();

            AndroidUtilities.runOnUIThread(() -> {
                resetingDialogs = false;
                applyDialogsNotificationsSettings(dialogsRes.dialogs);
                if (!UserConfig.getInstance(currentAccount).draftsLoaded) {
                    DataQuery.getInstance(currentAccount).loadDrafts();
                }

                putUsers(dialogsRes.users, false);
                putChats(dialogsRes.chats, false);

                for (int a = 0; a < dialogs.size(); a++) {
                    TLRPC.TL_dialog oldDialog = dialogs.get(a);
                    if ((int) oldDialog.id != 0) {
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
                    TLRPC.TL_dialog value = new_dialogs_dict.valueAt(a);
                    if (value.draft instanceof TLRPC.TL_draftMessage) {
                        DataQuery.getInstance(currentAccount).saveDraft(value.id, value.draft, null, false);
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

                dialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    dialogs.add(dialogs_dict.valueAt(a));
                }
                sortDialogs(null);
                dialogsEndReached = true;
                serverDialogsEndReached = false;

                if (UserConfig.getInstance(currentAccount).totalDialogsLoadCount < 400 && UserConfig.getInstance(currentAccount).dialogsLoadOffsetId != -1 && UserConfig.getInstance(currentAccount).dialogsLoadOffsetId != Integer.MAX_VALUE) {
                    loadDialogs(0, 100, false);
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    try {
                        int offsetId;
                        UserConfig.getInstance(currentAccount).totalDialogsLoadCount += dialogsRes.dialogs.size();
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
                            UserConfig.getInstance(currentAccount).dialogsLoadOffsetId = Integer.MAX_VALUE;
                            UserConfig.getInstance(currentAccount).dialogsLoadOffsetDate = UserConfig.getInstance(currentAccount).migrateOffsetDate;
                            UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId = UserConfig.getInstance(currentAccount).migrateOffsetUserId;
                            UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId = UserConfig.getInstance(currentAccount).migrateOffsetChatId;
                            UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId = UserConfig.getInstance(currentAccount).migrateOffsetChannelId;
                            UserConfig.getInstance(currentAccount).dialogsLoadOffsetAccess = UserConfig.getInstance(currentAccount).migrateOffsetAccess;
                            offsetId = -1;
                        }

                        StringBuilder dids = new StringBuilder(dialogsRes.dialogs.size() * 12);
                        LongSparseArray<TLRPC.TL_dialog> dialogHashMap = new LongSparseArray<>();
                        for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                            TLRPC.TL_dialog dialog = dialogsRes.dialogs.get(a);
                            if (dialog.peer.channel_id != 0) {
                                dialog.id = -dialog.peer.channel_id;
                            } else if (dialog.peer.chat_id != 0) {
                                dialog.id = -dialog.peer.chat_id;
                            } else {
                                dialog.id = dialog.peer.user_id;
                            }
                            if (dids.length() > 0) {
                                dids.append(",");
                            }
                            dids.append(dialog.id);
                            dialogHashMap.put(dialog.id, dialog);
                        }
                        SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT did FROM dialogs WHERE did IN (%s)", dids.toString()));
                        while (cursor.next()) {
                            long did = cursor.longValue(0);
                            TLRPC.TL_dialog dialog = dialogHashMap.get(did);
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
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT min(date) FROM dialogs WHERE date != 0 AND did >> 32 IN (0, -1)");
                        if (cursor.next()) {
                            int date = Math.max(1441062000, cursor.intValue(0));
                            for (int a = 0; a < dialogsRes.messages.size(); a++) {
                                TLRPC.Message message = dialogsRes.messages.get(a);
                                if (message.date < date) {
                                    if (offset != -1) {
                                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetId = UserConfig.getInstance(currentAccount).migrateOffsetId;
                                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetDate = UserConfig.getInstance(currentAccount).migrateOffsetDate;
                                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId = UserConfig.getInstance(currentAccount).migrateOffsetUserId;
                                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId = UserConfig.getInstance(currentAccount).migrateOffsetChatId;
                                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId = UserConfig.getInstance(currentAccount).migrateOffsetChannelId;
                                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetAccess = UserConfig.getInstance(currentAccount).migrateOffsetAccess;
                                        offsetId = -1;
                                        if (BuildVars.LOGS_ENABLED) {
                                            FileLog.d("migrate stop due to reached loaded dialogs " + LocaleController.getInstance().formatterStats.format((long) date * 1000));
                                        }
                                    }
                                    dialogsRes.messages.remove(a);
                                    a--;
                                    long did = MessageObject.getDialogId(message);
                                    TLRPC.TL_dialog dialog = dialogHashMap.get(did);
                                    dialogHashMap.remove(did);
                                    if (dialog != null) {
                                        dialogsRes.dialogs.remove(dialog);
                                    }
                                }
                            }
                            if (lastMessage != null && lastMessage.date < date && offset != -1) {
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetId = UserConfig.getInstance(currentAccount).migrateOffsetId;
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetDate = UserConfig.getInstance(currentAccount).migrateOffsetDate;
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId = UserConfig.getInstance(currentAccount).migrateOffsetUserId;
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId = UserConfig.getInstance(currentAccount).migrateOffsetChatId;
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId = UserConfig.getInstance(currentAccount).migrateOffsetChannelId;
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetAccess = UserConfig.getInstance(currentAccount).migrateOffsetAccess;
                                offsetId = -1;
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("migrate stop due to reached loaded dialogs " + LocaleController.getInstance().formatterStats.format((long) date * 1000));
                                }
                            }
                        }
                        cursor.dispose();

                        UserConfig.getInstance(currentAccount).migrateOffsetDate = lastMessage.date;
                        if (lastMessage.to_id.channel_id != 0) {
                            UserConfig.getInstance(currentAccount).migrateOffsetChannelId = lastMessage.to_id.channel_id;
                            UserConfig.getInstance(currentAccount).migrateOffsetChatId = 0;
                            UserConfig.getInstance(currentAccount).migrateOffsetUserId = 0;
                            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                TLRPC.Chat chat = dialogsRes.chats.get(a);
                                if (chat.id == UserConfig.getInstance(currentAccount).migrateOffsetChannelId) {
                                    UserConfig.getInstance(currentAccount).migrateOffsetAccess = chat.access_hash;
                                    break;
                                }
                            }
                        } else if (lastMessage.to_id.chat_id != 0) {
                            UserConfig.getInstance(currentAccount).migrateOffsetChatId = lastMessage.to_id.chat_id;
                            UserConfig.getInstance(currentAccount).migrateOffsetChannelId = 0;
                            UserConfig.getInstance(currentAccount).migrateOffsetUserId = 0;
                            for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                TLRPC.Chat chat = dialogsRes.chats.get(a);
                                if (chat.id == UserConfig.getInstance(currentAccount).migrateOffsetChatId) {
                                    UserConfig.getInstance(currentAccount).migrateOffsetAccess = chat.access_hash;
                                    break;
                                }
                            }
                        } else if (lastMessage.to_id.user_id != 0) {
                            UserConfig.getInstance(currentAccount).migrateOffsetUserId = lastMessage.to_id.user_id;
                            UserConfig.getInstance(currentAccount).migrateOffsetChatId = 0;
                            UserConfig.getInstance(currentAccount).migrateOffsetChannelId = 0;
                            for (int a = 0; a < dialogsRes.users.size(); a++) {
                                TLRPC.User user = dialogsRes.users.get(a);
                                if (user.id == UserConfig.getInstance(currentAccount).migrateOffsetUserId) {
                                    UserConfig.getInstance(currentAccount).migrateOffsetAccess = user.access_hash;
                                    break;
                                }
                            }
                        }

                        processLoadedDialogs(dialogsRes, null, offsetId, 0, 0, false, true, false);
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

    public void processLoadedDialogs(final TLRPC.messages_Dialogs dialogsRes, final ArrayList<TLRPC.EncryptedChat> encChats, final int offset, final int count, final int loadType, final boolean resetEnd, final boolean migrate, final boolean fromCache) {
        Utilities.stageQueue.postRunnable(() -> {
            if (!firstGettingTask) {
                getNewDeleteTask(null, 0);
                firstGettingTask = true;
            }

            if (BuildVars.LOGS_ENABLED) {
                FileLog.d("loaded loadType " + loadType + " count " + dialogsRes.dialogs.size());
            }
            if (loadType == 1 && dialogsRes.dialogs.size() == 0) {
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(dialogsRes.users, true);
                    loadingDialogs = false;
                    if (resetEnd) {
                        dialogsEndReached = false;
                        serverDialogsEndReached = false;
                    } else if (UserConfig.getInstance(currentAccount).dialogsLoadOffsetId == Integer.MAX_VALUE) {
                        dialogsEndReached = true;
                        serverDialogsEndReached = true;
                    } else {
                        loadDialogs(0, count, false);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                });
                return;
            }

            final LongSparseArray<TLRPC.TL_dialog> new_dialogs_dict = new LongSparseArray<>();
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
            if (loadType == 1) {
                nextDialogsCacheOffset = offset + count;
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

            if (!fromCache && !migrate && UserConfig.getInstance(currentAccount).dialogsLoadOffsetId != -1 && loadType == 0) {
                if (lastMessage != null && lastMessage.id != UserConfig.getInstance(currentAccount).dialogsLoadOffsetId) {
                    UserConfig.getInstance(currentAccount).totalDialogsLoadCount += dialogsRes.dialogs.size();
                    UserConfig.getInstance(currentAccount).dialogsLoadOffsetId = lastMessage.id;
                    UserConfig.getInstance(currentAccount).dialogsLoadOffsetDate = lastMessage.date;
                    if (lastMessage.to_id.channel_id != 0) {
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId = lastMessage.to_id.channel_id;
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId = 0;
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId = 0;
                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                            if (chat.id == UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId) {
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetAccess = chat.access_hash;
                                break;
                            }
                        }
                    } else if (lastMessage.to_id.chat_id != 0) {
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId = lastMessage.to_id.chat_id;
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId = 0;
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId = 0;
                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                            if (chat.id == UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId) {
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetAccess = chat.access_hash;
                                break;
                            }
                        }
                    } else if (lastMessage.to_id.user_id != 0) {
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId = lastMessage.to_id.user_id;
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChatId = 0;
                        UserConfig.getInstance(currentAccount).dialogsLoadOffsetChannelId = 0;
                        for (int a = 0; a < dialogsRes.users.size(); a++) {
                            TLRPC.User user = dialogsRes.users.get(a);
                            if (user.id == UserConfig.getInstance(currentAccount).dialogsLoadOffsetUserId) {
                                UserConfig.getInstance(currentAccount).dialogsLoadOffsetAccess = user.access_hash;
                                break;
                            }
                        }
                    }
                } else {
                    UserConfig.getInstance(currentAccount).dialogsLoadOffsetId = Integer.MAX_VALUE;
                }
                UserConfig.getInstance(currentAccount).saveConfig(false);
            }

            final ArrayList<TLRPC.TL_dialog> dialogsToReload = new ArrayList<>();
            for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                TLRPC.TL_dialog d = dialogsRes.dialogs.get(a);
                if (d.id == 0 && d.peer != null) {
                    if (d.peer.user_id != 0) {
                        d.id = d.peer.user_id;
                    } else if (d.peer.chat_id != 0) {
                        d.id = -d.peer.chat_id;
                    } else if (d.peer.channel_id != 0) {
                        d.id = -d.peer.channel_id;
                    }
                }
                if (d.id == 0) {
                    continue;
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

                if (allowCheck && loadType == 1 && (d.read_outbox_max_id == 0 || d.read_inbox_max_id == 0) && d.top_message != 0) {
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

            if (loadType != 1) {
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
                            value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
                            read_max.put(message.dialog_id, value);
                        }
                        message.unread = value < message.id;
                    }
                }
                MessagesStorage.getInstance(currentAccount).putDialogs(dialogsRes, 0);
            }
            if (loadType == 2) {
                TLRPC.Chat chat = dialogsRes.chats.get(0);
                getChannelDifference(chat.id);
                checkChannelInviter(chat.id);
            }

            AndroidUtilities.runOnUIThread(() -> {
                if (loadType != 1) {
                    applyDialogsNotificationsSettings(dialogsRes.dialogs);
                    if (!UserConfig.getInstance(currentAccount).draftsLoaded) {
                        DataQuery.getInstance(currentAccount).loadDrafts();
                    }
                }
                putUsers(dialogsRes.users, loadType == 1);
                putChats(dialogsRes.chats, loadType == 1);
                if (encChats != null) {
                    for (int a = 0; a < encChats.size(); a++) {
                        TLRPC.EncryptedChat encryptedChat = encChats.get(a);
                        if (encryptedChat instanceof TLRPC.TL_encryptedChat && AndroidUtilities.getMyLayerVersion(encryptedChat.layer) < SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
                            SecretChatHelper.getInstance(currentAccount).sendNotifyLayerMessage(encryptedChat, null);
                        }
                        putEncryptedChat(encryptedChat, true);
                    }
                }
                if (!migrate) {
                    loadingDialogs = false;
                }
                boolean added = false;

                int lastDialogDate = migrate && !dialogs.isEmpty() ? dialogs.get(dialogs.size() - 1).last_message_date : 0;
                for (int a = 0; a < new_dialogs_dict.size(); a++) {
                    long key = new_dialogs_dict.keyAt(a);
                    TLRPC.TL_dialog value = new_dialogs_dict.valueAt(a);
                    if (migrate && lastDialogDate != 0 && value.last_message_date < lastDialogDate) {
                        continue;
                    }
                    TLRPC.TL_dialog currentDialog = dialogs_dict.get(key);
                    if (loadType != 1 && value.draft instanceof TLRPC.TL_draftMessage) {
                        DataQuery.getInstance(currentAccount).saveDraft(value.id, value.draft, null, false);
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
                        if (loadType != 1) {
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

                dialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    dialogs.add(dialogs_dict.valueAt(a));
                }
                sortDialogs(migrate ? chatsDict : null);

                if (loadType != 2) {
                    if (!migrate) {
                        dialogsEndReached = (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && loadType == 0;
                        if (!fromCache) {
                            serverDialogsEndReached = (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && loadType == 0;
                        }
                    }
                }
                if (!fromCache && !migrate && UserConfig.getInstance(currentAccount).totalDialogsLoadCount < 400 && UserConfig.getInstance(currentAccount).dialogsLoadOffsetId != -1 && UserConfig.getInstance(currentAccount).dialogsLoadOffsetId != Integer.MAX_VALUE) {
                    loadDialogs(0, 100, false);
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);

                if (migrate) {
                    UserConfig.getInstance(currentAccount).migrateOffsetId = offset;
                    UserConfig.getInstance(currentAccount).saveConfig(false);
                    migratingDialogs = false;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
                } else {
                    generateUpdateMessage();
                    if (!added && loadType == 1) {
                        loadDialogs(0, count, false);
                    }
                }
                migrateDialogs(UserConfig.getInstance(currentAccount).migrateOffsetId, UserConfig.getInstance(currentAccount).migrateOffsetDate, UserConfig.getInstance(currentAccount).migrateOffsetUserId, UserConfig.getInstance(currentAccount).migrateOffsetChatId, UserConfig.getInstance(currentAccount).migrateOffsetChannelId, UserConfig.getInstance(currentAccount).migrateOffsetAccess);
                if (!dialogsToReload.isEmpty()) {
                    reloadDialogsReadValue(dialogsToReload, 0);
                }
                loadUnreadDialogs();
            });
        });
    }

    private void applyDialogNotificationsSettings(long dialog_id, TLRPC.PeerNotifySettings notify_settings) {
        int currentValue = notificationsPreferences.getInt("notify2_" + dialog_id, -1);
        int currentValue2 = notificationsPreferences.getInt("notifyuntil_" + dialog_id, 0);
        SharedPreferences.Editor editor = notificationsPreferences.edit();
        boolean updated = false;
        TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
        if (dialog != null) {
            dialog.notify_settings = notify_settings;
        }
        if ((notify_settings.flags & 2) != 0) {
            editor.putBoolean("silent_" + dialog_id, notify_settings.silent);
        } else {
            editor.remove("silent_" + dialog_id);
        }
        if ((notify_settings.flags & 4) != 0) {
            if (notify_settings.mute_until > ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                int until = 0;
                if (notify_settings.mute_until > ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24 * 365) {
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
                MessagesStorage.getInstance(currentAccount).setDialogFlags(dialog_id, ((long) until << 32) | 1);
                NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(dialog_id);
            } else {
                if (currentValue != 0 && currentValue != 1) {
                    updated = true;
                    if (dialog != null) {
                        dialog.notify_settings.mute_until = 0;
                    }
                    editor.putInt("notify2_" + dialog_id, 0);
                }
                MessagesStorage.getInstance(currentAccount).setDialogFlags(dialog_id, 0);
            }
        } else {
            if (currentValue != -1) {
                updated = true;
                if (dialog != null) {
                    dialog.notify_settings.mute_until = 0;
                }
                editor.remove("notify2_" + dialog_id);
            }
            MessagesStorage.getInstance(currentAccount).setDialogFlags(dialog_id, 0);
        }
        editor.commit();
        if (updated) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
        }
    }

    private void applyDialogsNotificationsSettings(ArrayList<TLRPC.TL_dialog> dialogs) {
        SharedPreferences.Editor editor = null;
        for (int a = 0; a < dialogs.size(); a++) {
            TLRPC.TL_dialog dialog = dialogs.get(a);
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
                    if (dialog.notify_settings.mute_until > ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                        if (dialog.notify_settings.mute_until > ConnectionsManager.getInstance(currentAccount).getCurrentTime() + 60 * 60 * 24 * 365) {
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
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                    if (res != null) {
                        int newCount;
                        if (res.count != 0) {
                            newCount = res.count;
                        } else {
                            newCount = res.messages.size();
                        }
                        MessagesStorage.getInstance(currentAccount).resetMentionsCount(dialog_id, newCount);
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
                    TLRPC.TL_dialog currentDialog = dialogs_dict.get(dialogId);
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
                    TLRPC.TL_dialog currentDialog = dialogs_dict.get(dialogId);
                    if (currentDialog != null) {
                        currentDialog.unread_mentions_count = dialogsMentionsToUpdate.valueAt(a);
                        if (createdDialogMainThreadIds.contains(currentDialog.id)) {
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateMentionsCount, currentDialog.id, currentDialog.unread_mentions_count);
                        }
                    }
                }
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
            if (dialogsToUpdate != null) {
                NotificationsController.getInstance(currentAccount).processDialogsUpdateRead(dialogsToUpdate);
            }
        });
    }

    protected void checkLastDialogMessage(final TLRPC.TL_dialog dialog, final TLRPC.InputPeer peer, long taskId) {
        final int lower_id = (int) dialog.id;
        if (lower_id == 0 || checkingLastMessagesDialogs.indexOfKey(lower_id) >= 0) {
            return;
        }
        TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
        req.peer = peer == null ? getInputPeer(lower_id) : peer;
        if (req.peer == null || req.peer instanceof TLRPC.TL_inputPeerChannel) {
            return;
        }
        req.limit = 1;
        checkingLastMessagesDialogs.put(lower_id, true);

        final long newTaskId;
        if (taskId == 0) {
            NativeByteBuffer data = null;
            try {
                data = new NativeByteBuffer(48 + req.peer.getObjectSize());
                data.writeInt32(10);
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
                peer.serializeToStream(data);
            } catch (Exception e) {
                FileLog.e(e);
            }
            newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
        } else {
            newTaskId = taskId;
        }

        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                if (!res.messages.isEmpty()) {
                    TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                    TLRPC.Message newMessage = res.messages.get(0);
                    TLRPC.TL_dialog newDialog = new TLRPC.TL_dialog();
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
                    newMessage.dialog_id = newDialog.id = dialog.id;
                    dialogs.users.addAll(res.users);
                    dialogs.chats.addAll(res.chats);
                    dialogs.dialogs.add(newDialog);
                    dialogs.messages.addAll(res.messages);
                    dialogs.count = 1;
                    processDialogsUpdate(dialogs, null);
                    MessagesStorage.getInstance(currentAccount).putMessages(res.messages, true, true, false, DownloadController.getInstance(currentAccount).getAutodownloadMask(), true);
                } else {
                    AndroidUtilities.runOnUIThread(() -> {
                        TLRPC.TL_dialog currentDialog = dialogs_dict.get(dialog.id);
                        if (currentDialog != null && currentDialog.top_message == 0) {
                            deleteDialog(dialog.id, 3);
                        }
                    });
                }
            }
            if (newTaskId != 0) {
                MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
            }
            AndroidUtilities.runOnUIThread(() -> checkingLastMessagesDialogs.delete(lower_id));
        });
    }

    public void processDialogsUpdate(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats) {
        Utilities.stageQueue.postRunnable(() -> {
            final LongSparseArray<TLRPC.TL_dialog> new_dialogs_dict = new LongSparseArray<>();
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
                TLRPC.TL_dialog d = dialogsRes.dialogs.get(a);
                if (d.id == 0) {
                    if (d.peer.user_id != 0) {
                        d.id = d.peer.user_id;
                    } else if (d.peer.chat_id != 0) {
                        d.id = -d.peer.chat_id;
                    } else if (d.peer.channel_id != 0) {
                        d.id = -d.peer.channel_id;
                    }
                }
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
                    TLRPC.TL_dialog value = new_dialogs_dict.valueAt(a);
                    TLRPC.TL_dialog currentDialog = dialogs_dict.get(key);
                    if (currentDialog == null) {
                        nextDialogsCacheOffset++;
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
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateMentionsCount, currentDialog.id, currentDialog.unread_mentions_count);
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

                dialogs.clear();
                for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                    dialogs.add(dialogs_dict.valueAt(a));
                }
                sortDialogs(null);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                NotificationsController.getInstance(currentAccount).processDialogsUpdateRead(dialogsToUpdate);
            });
        });
    }

    public void addToViewsQueue(final TLRPC.Message message) {
        Utilities.stageQueue.postRunnable(() -> {
            int peer;
            if (message.to_id.channel_id != 0) {
                peer = -message.to_id.channel_id;
            } else if (message.to_id.chat_id != 0) {
                peer = -message.to_id.chat_id;
            } else {
                peer = message.to_id.user_id;
            }
            ArrayList<Integer> ids = channelViewsToSend.get(peer);
            if (ids == null) {
                ids = new ArrayList<>();
                channelViewsToSend.put(peer, ids);
            }
            if (!ids.contains(message.id)) {
                ids.add(message.id);
            }
        });
    }

    public void markMessageContentAsRead(final MessageObject messageObject) {
        ArrayList<Long> arrayList = new ArrayList<>();
        long messageId = messageObject.getId();
        if (messageObject.messageOwner.to_id.channel_id != 0) {
            messageId |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
        }
        if (messageObject.messageOwner.mentioned) {
            MessagesStorage.getInstance(currentAccount).markMentionMessageAsRead(messageObject.getId(), messageObject.messageOwner.to_id.channel_id, messageObject.getDialogId());
        }
        arrayList.add(messageId);
        MessagesStorage.getInstance(currentAccount).markMessagesContentAsRead(arrayList, 0);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesReadContent, arrayList);
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
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

                });
            } else {
                TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
                req.id.add(messageObject.getId());
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (error == null) {
                        TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    }
                });
            }
        }
    }

    public void markMentionMessageAsRead(final int mid, final int channelId, final long did) {
        MessagesStorage.getInstance(currentAccount).markMentionMessageAsRead(mid, channelId, did);
        if (channelId != 0) {
            TLRPC.TL_channels_readMessageContents req = new TLRPC.TL_channels_readMessageContents();
            req.channel = getInputChannel(channelId);
            if (req.channel == null) {
                return;
            }
            req.id.add(mid);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            });
        } else {
            TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
            req.id.add(mid);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                }
            });
        }
    }

    public void markMessageAsRead(final int mid, final int channelId, int ttl) {
        if (mid == 0 || ttl <= 0) {
            return;
        }
        int time = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        MessagesStorage.getInstance(currentAccount).createTaskForMid(mid, channelId, time, time, ttl, false);
        if (channelId != 0) {
            TLRPC.TL_channels_readMessageContents req = new TLRPC.TL_channels_readMessageContents();
            req.channel = getInputChannel(channelId);
            req.id.add(mid);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            });
        } else {
            TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
            req.id.add(mid);
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error == null) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
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
        SecretChatHelper.getInstance(currentAccount).sendMessagesReadMessage(chat, random_ids, null);
        if (ttl > 0) {
            int time = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            MessagesStorage.getInstance(currentAccount).createTaskForSecretChat(chat.id, time, time, 0, random_ids);
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

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
        MessagesStorage.getInstance(currentAccount).resetMentionsCount(dialogId, 0);
        TLRPC.TL_messages_readMentions req = new TLRPC.TL_messages_readMentions();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer((int) dialogId);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }

    public void markDialogAsRead(final long dialogId, final int maxPositiveId, final int maxNegativeId, final int maxDate, final boolean popup, final int countDiff, final boolean readNow) {
        int lower_part = (int) dialogId;
        int high_id = (int) (dialogId >> 32);
        boolean createReadTask;

        if (lower_part != 0) {
            if (maxPositiveId == 0 || high_id == 1) {
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

            MessagesStorage.getInstance(currentAccount).processPendingRead(dialogId, maxMessageId, minMessageId, maxDate, isChannel);
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                TLRPC.TL_dialog dialog = dialogs_dict.get(dialogId);
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
                    if ((prevCount != 0 || dialog.unread_mark) && dialog.unread_count == 0 && !isDialogMuted(dialogId)) {
                        unreadUnmutedDialogs--;
                    }
                    if (dialog.unread_mark) {
                        dialog.unread_mark = false;
                        MessagesStorage.getInstance(currentAccount).setDialogUnread(dialog.id, false);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                }
                if (!popup) {
                    NotificationsController.getInstance(currentAccount).processReadMessages(null, dialogId, 0, maxPositiveId, false);
                    LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>(1);
                    dialogsToUpdate.put(dialogId, 0);
                    NotificationsController.getInstance(currentAccount).processDialogsUpdateRead(dialogsToUpdate);
                } else {
                    NotificationsController.getInstance(currentAccount).processReadMessages(null, dialogId, 0, maxPositiveId, true);
                    LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>(1);
                    dialogsToUpdate.put(dialogId, -1);
                    NotificationsController.getInstance(currentAccount).processDialogsUpdateRead(dialogsToUpdate);
                }
            }));

            createReadTask = maxPositiveId != Integer.MAX_VALUE;
        } else {
            if (maxDate == 0) {
                return;
            }
            createReadTask = true;

            TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
            MessagesStorage.getInstance(currentAccount).processPendingRead(dialogId, maxPositiveId, maxNegativeId, maxDate, false);
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                NotificationsController.getInstance(currentAccount).processReadMessages(null, dialogId, maxDate, 0, popup);
                TLRPC.TL_dialog dialog = dialogs_dict.get(dialogId);
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
                    if ((prevCount != 0 || dialog.unread_mark) && dialog.unread_count == 0 && !isDialogMuted(dialogId)) {
                        unreadUnmutedDialogs--;
                    }
                    if (dialog.unread_mark) {
                        dialog.unread_mark = false;
                        MessagesStorage.getInstance(currentAccount).setDialogUnread(dialog.id, false);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                }
                LongSparseArray<Integer> dialogsToUpdate = new LongSparseArray<>(1);
                dialogsToUpdate.put(dialogId, 0);
                NotificationsController.getInstance(currentAccount).processDialogsUpdateRead(dialogsToUpdate);
            }));

            if (chat != null && chat.ttl > 0) {
                int serverTime = Math.max(ConnectionsManager.getInstance(currentAccount).getCurrentTime(), maxDate);
                MessagesStorage.getInstance(currentAccount).createTaskForSecretChat(chat.id, serverTime, serverTime, 0, null);
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

    public int createChat(String title, ArrayList<Integer> selectedContacts, final String about, int type, final BaseFragment fragment) {
        if (type == ChatObject.CHAT_TYPE_BROADCAST) {
            TLRPC.TL_chat chat = new TLRPC.TL_chat();
            chat.id = UserConfig.getInstance(currentAccount).lastBroadcastId;
            chat.title = title;
            chat.photo = new TLRPC.TL_chatPhotoEmpty();
            chat.participants_count = selectedContacts.size();
            chat.date = (int) (System.currentTimeMillis() / 1000);
            chat.version = 1;
            UserConfig.getInstance(currentAccount).lastBroadcastId--;
            putChat(chat, false);
            ArrayList<TLRPC.Chat> chatsArrays = new ArrayList<>();
            chatsArrays.add(chat);
            MessagesStorage.getInstance(currentAccount).putUsersAndChats(null, chatsArrays, true, true);

            TLRPC.TL_chatFull chatFull = new TLRPC.TL_chatFull();
            chatFull.id = chat.id;
            chatFull.chat_photo = new TLRPC.TL_photoEmpty();
            chatFull.notify_settings = new TLRPC.TL_peerNotifySettingsEmpty_layer77();
            chatFull.exported_invite = new TLRPC.TL_chatInviteEmpty();
            chatFull.participants = new TLRPC.TL_chatParticipants();
            chatFull.participants.chat_id = chat.id;
            chatFull.participants.admin_id = UserConfig.getInstance(currentAccount).getClientUserId();
            chatFull.participants.version = 1;
            for (int a = 0; a < selectedContacts.size(); a++) {
                TLRPC.TL_chatParticipant participant = new TLRPC.TL_chatParticipant();
                participant.user_id = selectedContacts.get(a);
                participant.inviter_id = UserConfig.getInstance(currentAccount).getClientUserId();
                participant.date = (int) (System.currentTimeMillis() / 1000);
                chatFull.participants.participants.add(participant);
            }
            MessagesStorage.getInstance(currentAccount).updateChatInfo(chatFull, false);

            TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();
            newMsg.action = new TLRPC.TL_messageActionCreatedBroadcastList();
            newMsg.local_id = newMsg.id = UserConfig.getInstance(currentAccount).getNewMessageId();
            newMsg.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
            newMsg.dialog_id = AndroidUtilities.makeBroadcastId(chat.id);
            newMsg.to_id = new TLRPC.TL_peerChat();
            newMsg.to_id.chat_id = chat.id;
            newMsg.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
            newMsg.random_id = 0;
            newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
            UserConfig.getInstance(currentAccount).saveConfig(false);
            MessageObject newMsgObj = new MessageObject(currentAccount, newMsg, users, true);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;

            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            arr.add(newMsg);
            MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0);
            updateInterfaceWithMessages(newMsg.dialog_id, objArr);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatDidCreated, chat.id);

            return 0;
        } else if (type == ChatObject.CHAT_TYPE_CHAT) {
            final TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
            req.title = title;
            for (int a = 0; a < selectedContacts.size(); a++) {
                TLRPC.User user = getUser(selectedContacts.get(a));
                if (user == null) {
                    continue;
                }
                req.users.add(getInputUser(user));
            }
            return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        AlertsCreator.processError(currentAccount, error, fragment, req);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatDidFailCreate);
                    });
                    return;
                }
                final TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(updates.users, false);
                    putChats(updates.chats, false);
                    if (updates.chats != null && !updates.chats.isEmpty()) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatDidCreated, updates.chats.get(0).id);
                    } else {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatDidFailCreate);
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } else if (type == ChatObject.CHAT_TYPE_CHANNEL || type == ChatObject.CHAT_TYPE_MEGAGROUP) {
            final TLRPC.TL_channels_createChannel req = new TLRPC.TL_channels_createChannel();
            req.title = title;
            req.about = about;
            if (type == ChatObject.CHAT_TYPE_MEGAGROUP) {
                req.megagroup = true;
            } else {
                req.broadcast = true;
            }
            return ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> {
                        AlertsCreator.processError(currentAccount, error, fragment, req);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatDidFailCreate);
                    });
                    return;
                }
                final TLRPC.Updates updates = (TLRPC.Updates) response;
                processUpdates(updates, false);
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(updates.users, false);
                    putChats(updates.chats, false);
                    if (updates.chats != null && !updates.chats.isEmpty()) {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatDidCreated, updates.chats.get(0).id);
                    } else {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatDidFailCreate);
                    }
                });
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
        return 0;
    }

    public void convertToMegaGroup(final Context context, int chat_id) {
        TLRPC.TL_messages_migrateChat req = new TLRPC.TL_messages_migrateChat();
        req.chat_id = chat_id;
        final AlertDialog progressDialog = new AlertDialog(context, 1);
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
            } else {
                AndroidUtilities.runOnUIThread(() -> {
                    if (!((Activity) context).isFinishing()) {
                        try {
                            progressDialog.dismiss();
                        } catch (Exception e) {
                            FileLog.e(e);
                        }
                        AlertDialog.Builder builder = new AlertDialog.Builder(context);
                        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                        builder.setMessage(LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
                        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                        builder.show().setCanceledOnTouchOutside(true);
                    }
                });
            }
        });
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            try {
                dialog.dismiss();
            } catch (Exception e) {
                FileLog.e(e);
            }
        });
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null) {
                AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, fragment, req, true));
                return;
            }
            processUpdates((TLRPC.Updates) response, false);
        });
    }

    public void toogleChannelInvites(int chat_id, boolean enabled) {
        TLRPC.TL_channels_toggleInvites req = new TLRPC.TL_channels_toggleInvites();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                processUpdates((TLRPC.Updates) response, false);
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void toogleChannelSignatures(int chat_id, boolean enabled) {
        TLRPC.TL_channels_toggleSignatures req = new TLRPC.TL_channels_toggleSignatures();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHANNEL));
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void toogleChannelInvitesHistory(int chat_id, boolean enabled) {
        TLRPC.TL_channels_togglePreHistoryHidden req = new TLRPC.TL_channels_togglePreHistoryHidden();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHANNEL));
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void updateChannelAbout(int chat_id, final String about, final TLRPC.ChatFull info) {
        if (info == null) {
            return;
        }
        TLRPC.TL_channels_editAbout req = new TLRPC.TL_channels_editAbout();
        req.channel = getInputChannel(chat_id);
        req.about = about;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                AndroidUtilities.runOnUIThread(() -> {
                    info.about = about;
                    MessagesStorage.getInstance(currentAccount).updateChatInfo(info, false);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, false, null);
                });
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void updateChannelUserName(final int chat_id, final String userName) {
        TLRPC.TL_channels_updateUsername req = new TLRPC.TL_channels_updateUsername();
        req.channel = getInputChannel(chat_id);
        req.username = userName;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(null, arrayList, true, true);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHANNEL);
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error != null) {
                return;
            }
            processUpdates((TLRPC.Updates) response, false);
        });
    }

    public void toggleAdminMode(final int chat_id, boolean enabled) {
        TLRPC.TL_messages_toggleChatAdmins req = new TLRPC.TL_messages_toggleChatAdmins();
        req.chat_id = chat_id;
        req.enabled = enabled;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                processUpdates((TLRPC.Updates) response, false);
                loadFullChat(chat_id, 0, true);
            }
        });
    }

    public void toggleUserAdmin(final int chat_id, int user_id, boolean admin) {
        TLRPC.TL_messages_editChatAdmin req = new TLRPC.TL_messages_editChatAdmin();
        req.chat_id = chat_id;
        req.user_id = getInputUser(user_id);
        req.is_admin = admin;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

        });
    }

    public void addUserToChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info, int count_fwd, String botHash, final BaseFragment fragment) {
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

            ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                    AndroidUtilities.runOnUIThread(() -> joiningToChannels.remove((Integer) chat_id));
                }
                if (error != null) {
                    AndroidUtilities.runOnUIThread(() -> AlertsCreator.processError(currentAccount, error, fragment, request, isChannel && !isMegagroup));
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
                    MessagesStorage.getInstance(currentAccount).updateDialogsWithDeletedMessages(new ArrayList<>(), null, true, chat_id);
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
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(null, chatArrayList, true, true);

                TLRPC.TL_chatParticipant newPart = new TLRPC.TL_chatParticipant();
                newPart.user_id = user.id;
                newPart.inviter_id = UserConfig.getInstance(currentAccount).getClientUserId();
                newPart.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                info.participants.participants.add(0, newPart);
                MessagesStorage.getInstance(currentAccount).updateChatInfo(info, true);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, false, null);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
            }
        }
    }

    public void deleteUserFromChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info) {
        deleteUserFromChat(chat_id, user, info, false);
    }

    public void deleteUserFromChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info, boolean forceDelete) {
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
                    req.banned_rights = new TLRPC.TL_channelBannedRights();
                    req.banned_rights.view_messages = true;
                    req.banned_rights.send_media = true;
                    req.banned_rights.send_messages = true;
                    req.banned_rights.send_stickers = true;
                    req.banned_rights.send_gifs = true;
                    req.banned_rights.send_games = true;
                    req.banned_rights.send_inline = true;
                    req.banned_rights.embed_links = true;
                    request = req;
                }
            } else {
                TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
                req.chat_id = chat_id;
                req.user_id = getInputUser(user);
                request = req;
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
                if (user.id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                    AndroidUtilities.runOnUIThread(() -> deleteDialog(-chat_id, 0));
                }
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
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(null, chatArrayList, true, true);

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
                    MessagesStorage.getInstance(currentAccount).updateChatInfo(info, true);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, false, null);
                }
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
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
            MessagesStorage.getInstance(currentAccount).putUsersAndChats(null, chatArrayList, true, true);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_NAME);
        }
    }

    public void changeChatAvatar(int chat_id, TLRPC.InputFile uploadedAvatar) {
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(request, (response, error) -> {
            if (error != null) {
                return;
            }
            processUpdates((TLRPC.Updates) response, false);
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void unregistedPush() {
        if (UserConfig.getInstance(currentAccount).registeredForPush && SharedConfig.pushString.length() == 0) {
            TLRPC.TL_account_unregisterDevice req = new TLRPC.TL_account_unregisterDevice();
            req.token = SharedConfig.pushString;
            req.token_type = 2;
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                UserConfig userConfig = UserConfig.getInstance(a);
                if (a != currentAccount && userConfig.isClientActivated()) {
                    req.other_uids.add(userConfig.getClientUserId());
                }
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            });
        }
    }

    public void performLogout(int type) {
        if (type == 1) {
            unregistedPush();
            TLRPC.TL_auth_logOut req = new TLRPC.TL_auth_logOut();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> ConnectionsManager.getInstance(currentAccount).cleanup(false));
        } else {
            ConnectionsManager.getInstance(currentAccount).cleanup(type == 2);
        }
        UserConfig.getInstance(currentAccount).clearConfig();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.appDidLogout);
        MessagesStorage.getInstance(currentAccount).cleanup(false);
        cleanup();
        ContactsController.getInstance(currentAccount).deleteUnknownAppAccounts();
    }

    public void generateUpdateMessage() {
        if (BuildVars.DEBUG_VERSION || SharedConfig.lastUpdateVersion == null || SharedConfig.lastUpdateVersion.equals(BuildVars.BUILD_VERSION_STRING)) {
            return;
        }
        TLRPC.TL_help_getAppChangelog req = new TLRPC.TL_help_getAppChangelog();
        req.prev_app_version = SharedConfig.lastUpdateVersion;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
        if (TextUtils.isEmpty(regid) || registeringForPush || UserConfig.getInstance(currentAccount).getClientUserId() == 0) {
            return;
        }
        if (UserConfig.getInstance(currentAccount).registeredForPush && regid.equals(SharedConfig.pushString)) {
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_boolTrue) {
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d("account " + currentAccount + " registered for push");
                }
                UserConfig.getInstance(currentAccount).registeredForPush = true;
                SharedConfig.pushString = regid;
                UserConfig.getInstance(currentAccount).saveConfig(false);
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            updatingState = false;
            if (error == null) {
                TLRPC.TL_updates_state res = (TLRPC.TL_updates_state) response;
                MessagesStorage.getInstance(currentAccount).setLastDateValue(res.date);
                MessagesStorage.getInstance(currentAccount).setLastPtsValue(res.pts);
                MessagesStorage.getInstance(currentAccount).setLastSeqValue(res.seq);
                MessagesStorage.getInstance(currentAccount).setLastQtsValue(res.qts);
                for (int a = 0; a < 3; a++) {
                    processUpdatesQueue(a, 2);
                }
                MessagesStorage.getInstance(currentAccount).saveDiffParams(MessagesStorage.getInstance(currentAccount).getLastSeqValue(), MessagesStorage.getInstance(currentAccount).getLastPtsValue(), MessagesStorage.getInstance(currentAccount).getLastDateValue(), MessagesStorage.getInstance(currentAccount).getLastQtsValue());
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
            if (MessagesStorage.getInstance(currentAccount).getLastSeqValue() + 1 == seq || MessagesStorage.getInstance(currentAccount).getLastSeqValue() == seq) {
                return 0;
            } else if (MessagesStorage.getInstance(currentAccount).getLastSeqValue() < seq) {
                return 1;
            } else {
                return 2;
            }
        } else if (type == 1) {
            if (updates.pts <= MessagesStorage.getInstance(currentAccount).getLastPtsValue()) {
                return 2;
            } else if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() + updates.pts_count == updates.pts) {
                return 0;
            } else {
                return 1;
            }
        } else if (type == 2) {
            if (updates.pts <= MessagesStorage.getInstance(currentAccount).getLastQtsValue()) {
                return 2;
            } else if (MessagesStorage.getInstance(currentAccount).getLastQtsValue() + updates.updates.size() == updates.pts) {
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
                    MessagesStorage.getInstance(currentAccount).setLastSeqValue(getUpdateSeq(updates));
                } else if (type == 1) {
                    MessagesStorage.getInstance(currentAccount).setLastPtsValue(updates.pts);
                } else {
                    MessagesStorage.getInstance(currentAccount).setLastQtsValue(updates.pts);
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
                MessagesStorage.getInstance(currentAccount).removePendingTask(taskId);
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
            newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
        } else {
            newTaskId = taskId;
        }
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                if (!res.dialogs.isEmpty() && !res.chats.isEmpty()) {
                    TLRPC.TL_messages_dialogs dialogs = new TLRPC.TL_messages_dialogs();
                    dialogs.dialogs.addAll(res.dialogs);
                    dialogs.messages.addAll(res.messages);
                    dialogs.users.addAll(res.users);
                    dialogs.chats.addAll(res.chats);
                    processLoadedDialogs(dialogs, null, 0, 1, 2, false, false, false);
                }
            }
            if (newTaskId != 0) {
                MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
            }
            gettingUnknownChannels.delete(channel.id);
        });
    }

    public void startShortPoll(final int channelId, final boolean stop) {
        Utilities.stageQueue.postRunnable(() -> {
            if (stop) {
                needShortPollChannels.delete(channelId);
            } else {
                needShortPollChannels.put(channelId, 0);
                if (shortPollChannels.indexOfKey(channelId) < 0) {
                    getChannelDifference(channelId, 3, 0, null);
                }
            }
        });
    }

    private void getChannelDifference(final int channelId) {
        getChannelDifference(channelId, 0, 0, null);
    }

    public static boolean isSupportId(int id) {
        return id / 1000 == 777 || id == 333000 ||
                id == 4240000 || id == 4240000 || id == 4244000 ||
                id == 4245000 || id == 4246000 || id == 410000 ||
                id == 420000 || id == 431000 || id == 431415000 ||
                id == 434000 || id == 4243000 || id == 439000 ||
                id == 449000 || id == 450000 || id == 452000 ||
                id == 454000 || id == 4254000 || id == 455000 ||
                id == 460000 || id == 470000 || id == 479000 ||
                id == 796000 || id == 482000 || id == 490000 ||
                id == 496000 || id == 497000 || id == 498000 ||
                id == 4298000;
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
                channelPts = MessagesStorage.getInstance(currentAccount).getChannelPtsSync(channelId);
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
                chat = MessagesStorage.getInstance(currentAccount).getChatSync(channelId);
                if (chat != null) {
                    putChat(chat, true);
                }
            }
            inputChannel = getInputChannel(chat);
        }
        if (inputChannel == null || inputChannel.access_hash == 0) {
            if (taskId != 0) {
                MessagesStorage.getInstance(currentAccount).removePendingTask(taskId);
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
            newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
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
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
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

                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                AndroidUtilities.runOnUIThread(() -> {
                    putUsers(res.users, false);
                    putChats(res.chats, false);
                });

                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    if (!msgUpdates.isEmpty()) {
                        final SparseArray<long[]> corrected = new SparseArray<>();
                        for (TLRPC.TL_updateMessageID update : msgUpdates) {
                            long[] ids = MessagesStorage.getInstance(currentAccount).updateMessageStateAndId(update.random_id, null, update.id, 0, false, channelId);
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
                                    SendMessagesHelper.getInstance(currentAccount).processSentMessage(oldId);
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newId, null, ids[0], 0L);
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
                                    inboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
                                    dialogs_read_inbox_max.put(dialog_id, inboxValue);
                                }

                                Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                                if (outboxValue == null) {
                                    outboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, dialog_id);
                                    dialogs_read_outbox_max.put(dialog_id, outboxValue);
                                }

                                for (int a = 0; a < res.new_messages.size(); a++) {
                                    TLRPC.Message message = res.new_messages.get(a);
                                    message.unread = !(channelFinal != null && channelFinal.left || (message.out ? outboxValue : inboxValue) >= message.id || message.action instanceof TLRPC.TL_messageActionChannelCreate);
                                    if (channelFinal != null && channelFinal.megagroup) {
                                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                    }

                                    MessageObject obj = new MessageObject(currentAccount, message, usersDict, createdDialogIds.contains(dialog_id));
                                    if (!obj.isOut() && obj.isUnread()) {
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
                                        updateInterfaceWithMessages(key, value);
                                    }
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                                });
                                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                                    if (!pushMessages.isEmpty()) {
                                        AndroidUtilities.runOnUIThread(() -> NotificationsController.getInstance(currentAccount).processNewMessages(pushMessages, true, false));
                                    }
                                    MessagesStorage.getInstance(currentAccount).putMessages(res.new_messages, true, false, false, DownloadController.getInstance(currentAccount).getAutodownloadMask());
                                });
                            }

                            if (!res.other_updates.isEmpty()) {
                                processUpdateArray(res.other_updates, res.users, res.chats, true);
                            }
                            processChannelsUpdatesQueue(channelId, 1);
                            MessagesStorage.getInstance(currentAccount).saveChannelPts(channelId, res.pts);
                        } else if (res instanceof TLRPC.TL_updates_channelDifferenceTooLong) {
                            long dialog_id = -channelId;

                            Integer inboxValue = dialogs_read_inbox_max.get(dialog_id);
                            if (inboxValue == null) {
                                inboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
                                dialogs_read_inbox_max.put(dialog_id, inboxValue);
                            }

                            Integer outboxValue = dialogs_read_outbox_max.get(dialog_id);
                            if (outboxValue == null) {
                                outboxValue = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, dialog_id);
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
                            MessagesStorage.getInstance(currentAccount).overwriteChannel(channelId, (TLRPC.TL_updates_channelDifferenceTooLong) res, newDialogType);
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
                            MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
                        }
                    });
                });
            } else {
                AndroidUtilities.runOnUIThread(() -> checkChannelError(error.text, channelId));
                gettingDifferenceChannels.delete(channelId);
                if (newTaskId != 0) {
                    MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
                }
            }
        });
    }

    private void checkChannelError(String text, int channelId) {
        switch (text) {
            case "CHANNEL_PRIVATE":
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 0);
                break;
            case "CHANNEL_PUBLIC_GROUP_NA":
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 1);
                break;
            case "USER_BANNED_IN_CHANNEL":
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 2);
                break;
        }
    }

    public void getDifference() {
        getDifference(MessagesStorage.getInstance(currentAccount).getLastPtsValue(), MessagesStorage.getInstance(currentAccount).getLastDateValue(), MessagesStorage.getInstance(currentAccount).getLastQtsValue(), false);
    }

    public void getDifference(int pts, final int date, final int qts, boolean slice) {
        registerForPush(SharedConfig.pushString);
        if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() == 0) {
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
            if (ConnectionsManager.isConnectedOrConnectingToWiFi()) {
                req.pts_total_limit = 5000;
            } else {
                req.pts_total_limit = 1000;
            }
            getDifferenceFirstSync = false;
        }
        if (req.date == 0) {
            req.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        }
        if (BuildVars.LOGS_ENABLED) {
            FileLog.d("start getDifference with date = " + date + " pts = " + pts + " qts = " + qts);
        }
        ConnectionsManager.getInstance(currentAccount).setIsUpdating(true);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (error == null) {
                final TLRPC.updates_Difference res = (TLRPC.updates_Difference) response;
                if (res instanceof TLRPC.TL_updates_differenceTooLong) {
                    AndroidUtilities.runOnUIThread(() -> {
                        loadedFullUsers.clear();
                        loadedFullChats.clear();
                        resetDialogs(true, MessagesStorage.getInstance(currentAccount).getLastSeqValue(), res.pts, date, qts);
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
                                    channelPts = MessagesStorage.getInstance(currentAccount).getChannelPtsSync(channelId);
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

                    MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, false);
                        if (!msgUpdates.isEmpty()) {
                            final SparseArray<long[]> corrected = new SparseArray<>();
                            for (int a = 0; a < msgUpdates.size(); a++) {
                                TLRPC.TL_updateMessageID update = msgUpdates.get(a);
                                long[] ids = MessagesStorage.getInstance(currentAccount).updateMessageStateAndId(update.random_id, null, update.id, 0, false, 0);
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
                                        SendMessagesHelper.getInstance(currentAccount).processSentMessage(oldId);
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newId, null, ids[0], 0L);
                                    }
                                });
                            }
                        }

                        Utilities.stageQueue.postRunnable(() -> {
                            if (!res.new_messages.isEmpty() || !res.new_encrypted_messages.isEmpty()) {
                                final LongSparseArray<ArrayList<MessageObject>> messages = new LongSparseArray<>();
                                for (int b = 0; b < res.new_encrypted_messages.size(); b++) {
                                    TLRPC.EncryptedMessage encryptedMessage = res.new_encrypted_messages.get(b);
                                    ArrayList<TLRPC.Message> decryptedMessages = SecretChatHelper.getInstance(currentAccount).decryptMessage(encryptedMessage);
                                    if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                                        res.new_messages.addAll(decryptedMessages);
                                    }
                                }

                                ImageLoader.saveMessagesThumbs(res.new_messages);

                                final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                                int clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                                for (int a = 0; a < res.new_messages.size(); a++) {
                                    TLRPC.Message message = res.new_messages.get(a);
                                    if (message.dialog_id == 0) {
                                        if (message.to_id.chat_id != 0) {
                                            message.dialog_id = -message.to_id.chat_id;
                                        } else {
                                            if (message.to_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
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
                                                value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
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

                                    if (!obj.isOut() && obj.isUnread()) {
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
                                        updateInterfaceWithMessages(key, value);
                                    }
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                                });
                                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                                    if (!pushMessages.isEmpty()) {
                                        AndroidUtilities.runOnUIThread(() -> NotificationsController.getInstance(currentAccount).processNewMessages(pushMessages, !(res instanceof TLRPC.TL_updates_differenceSlice), false));
                                    }
                                    MessagesStorage.getInstance(currentAccount).putMessages(res.new_messages, true, false, false, DownloadController.getInstance(currentAccount).getAutodownloadMask());
                                });

                                SecretChatHelper.getInstance(currentAccount).processPendingEncMessages();
                            }

                            if (!res.other_updates.isEmpty()) {
                                processUpdateArray(res.other_updates, res.users, res.chats, true);
                            }

                            if (res instanceof TLRPC.TL_updates_difference) {
                                gettingDifference = false;
                                MessagesStorage.getInstance(currentAccount).setLastSeqValue(res.state.seq);
                                MessagesStorage.getInstance(currentAccount).setLastDateValue(res.state.date);
                                MessagesStorage.getInstance(currentAccount).setLastPtsValue(res.state.pts);
                                MessagesStorage.getInstance(currentAccount).setLastQtsValue(res.state.qts);
                                ConnectionsManager.getInstance(currentAccount).setIsUpdating(false);
                                for (int a = 0; a < 3; a++) {
                                    processUpdatesQueue(a, 1);
                                }
                            } else if (res instanceof TLRPC.TL_updates_differenceSlice) {
                                MessagesStorage.getInstance(currentAccount).setLastDateValue(res.intermediate_state.date);
                                MessagesStorage.getInstance(currentAccount).setLastPtsValue(res.intermediate_state.pts);
                                MessagesStorage.getInstance(currentAccount).setLastQtsValue(res.intermediate_state.qts);
                            } else if (res instanceof TLRPC.TL_updates_differenceEmpty) {
                                gettingDifference = false;
                                MessagesStorage.getInstance(currentAccount).setLastSeqValue(res.seq);
                                MessagesStorage.getInstance(currentAccount).setLastDateValue(res.date);
                                ConnectionsManager.getInstance(currentAccount).setIsUpdating(false);
                                for (int a = 0; a < 3; a++) {
                                    processUpdatesQueue(a, 1);
                                }
                            }
                            MessagesStorage.getInstance(currentAccount).saveDiffParams(MessagesStorage.getInstance(currentAccount).getLastSeqValue(), MessagesStorage.getInstance(currentAccount).getLastPtsValue(), MessagesStorage.getInstance(currentAccount).getLastDateValue(), MessagesStorage.getInstance(currentAccount).getLastQtsValue());
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d("received difference with date = " + MessagesStorage.getInstance(currentAccount).getLastDateValue() + " pts = " + MessagesStorage.getInstance(currentAccount).getLastPtsValue() + " seq = " + MessagesStorage.getInstance(currentAccount).getLastSeqValue() + " messages = " + res.new_messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                            }
                        });
                    });
                }
            } else {
                gettingDifference = false;
                ConnectionsManager.getInstance(currentAccount).setIsUpdating(false);
            }
        });
    }

    public boolean canPinDialog(boolean secret) {
        int count = 0;
        for (int a = 0; a < dialogs.size(); a++) {
            TLRPC.TL_dialog dialog = dialogs.get(a);
            int lower_id = (int) dialog.id;
            if (secret && lower_id != 0 || !secret && lower_id == 0) {
                continue;
            }
            if (dialog.pinned) {
                count++;
            }
        }
        return count < maxPinnedDialogsCount;
    }

    public void markDialogAsUnread(long did, TLRPC.InputPeer peer, long taskId) {
        TLRPC.TL_dialog dialog = dialogs_dict.get(did);
        if (dialog != null) {
            dialog.unread_mark = true;
            if (dialog.unread_count == 0 && !isDialogMuted(did)) {
                unreadUnmutedDialogs++;
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
            MessagesStorage.getInstance(currentAccount).setDialogUnread(did, true);
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
                newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
            } else {
                newTaskId = taskId;
            }

            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (newTaskId != 0) {
                    MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
                }
            });
        }
    }

    public void loadUnreadDialogs() {
        if (loadingUnreadDialogs || UserConfig.getInstance(currentAccount).unreadDialogsLoaded) {
            return;
        }
        loadingUnreadDialogs = true;
        TLRPC.TL_messages_getDialogUnreadMarks req = new TLRPC.TL_messages_getDialogUnreadMarks();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
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
                        MessagesStorage.getInstance(currentAccount).setDialogUnread(did, true);
                        TLRPC.TL_dialog dialog = dialogs_dict.get(did);
                        if (dialog != null && !dialog.unread_mark) {
                            dialog.unread_mark = true;
                            if (dialog.unread_count == 0 && !isDialogMuted(did)) {
                                unreadUnmutedDialogs++;
                            }
                        }
                    }
                }
                UserConfig.getInstance(currentAccount).unreadDialogsLoaded = true;
                UserConfig.getInstance(currentAccount).saveConfig(false);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                loadingUnreadDialogs = false;
            }
        }));
    }

    public boolean pinDialog(long did, boolean pin, TLRPC.InputPeer peer, long taskId) {
        int lower_id = (int) did;
        TLRPC.TL_dialog dialog = dialogs_dict.get(did);
        if (dialog == null || dialog.pinned == pin) {
            return dialog != null;
        }
        dialog.pinned = pin;
        if (pin) {
            int maxPinnedNum = 0;
            for (int a = 0; a < dialogs.size(); a++) {
                TLRPC.TL_dialog d = dialogs.get(a);
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
        if (!pin && dialogs.get(dialogs.size() - 1) == dialog && !dialogsEndReached) {
            dialogs.remove(dialogs.size() - 1);
        }
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
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
                    newTaskId = MessagesStorage.getInstance(currentAccount).createPendingTask(data);
                } else {
                    newTaskId = taskId;
                }

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                    if (newTaskId != 0) {
                        MessagesStorage.getInstance(currentAccount).removePendingTask(newTaskId);
                    }
                });
            }
        }
        MessagesStorage.getInstance(currentAccount).setDialogPinned(did, dialog.pinnedNum);
        return true;
    }

    public void loadPinnedDialogs(final long newDialogId, final ArrayList<Long> order) {
        if (UserConfig.getInstance(currentAccount).pinnedDialogsLoaded) {
            return;
        }
        TLRPC.TL_messages_getPinnedDialogs req = new TLRPC.TL_messages_getPinnedDialogs();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
            if (response != null) {
                final TLRPC.TL_messages_peerDialogs res = (TLRPC.TL_messages_peerDialogs) response;
                final TLRPC.TL_messages_dialogs toCache = new TLRPC.TL_messages_dialogs();
                toCache.users.addAll(res.users);
                toCache.chats.addAll(res.chats);
                toCache.dialogs.addAll(res.dialogs);
                toCache.messages.addAll(res.messages);

                final LongSparseArray<MessageObject> new_dialogMessage = new LongSparseArray<>();
                final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
                final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();
                final ArrayList<Long> newPinnedOrder = new ArrayList<>();

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
                for (int a = 0; a < res.dialogs.size(); a++) {
                    TLRPC.TL_dialog d = res.dialogs.get(a);
                    if (d.id == 0) {
                        if (d.peer.user_id != 0) {
                            d.id = d.peer.user_id;
                        } else if (d.peer.chat_id != 0) {
                            d.id = -d.peer.chat_id;
                        } else if (d.peer.channel_id != 0) {
                            d.id = -d.peer.channel_id;
                        }
                    }
                    newPinnedOrder.add(d.id);
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

                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    applyDialogsNotificationsSettings(res.dialogs);
                    boolean changed = false;
                    boolean added = false;
                    int maxPinnedNum = 0;
                    LongSparseArray<Integer> oldPinnedDialogNums = new LongSparseArray<>();
                    ArrayList<Long> oldPinnedOrder = new ArrayList<>();
                    for (int a = 0; a < dialogs.size(); a++) {
                        TLRPC.TL_dialog dialog = dialogs.get(a);
                        if ((int) dialog.id == 0) {
                            continue;
                        }
                        if (!dialog.pinned) {
                            break;
                        }
                        maxPinnedNum = Math.max(dialog.pinnedNum, maxPinnedNum);
                        oldPinnedDialogNums.put(dialog.id, dialog.pinnedNum);
                        oldPinnedOrder.add(dialog.id);
                        dialog.pinned = false;
                        dialog.pinnedNum = 0;
                        changed = true;
                    }

                    ArrayList<Long> pinnedDialogs = new ArrayList<>();
                    ArrayList<Long> orderArrayList = order != null ? order : newPinnedOrder;
                    if (orderArrayList.size() < oldPinnedOrder.size()) {
                        orderArrayList.add(0L);
                    }
                    while (oldPinnedOrder.size() < orderArrayList.size()) {
                        oldPinnedOrder.add(0, 0L);
                    }
                    if (!res.dialogs.isEmpty()) {
                        putUsers(res.users, false);
                        putChats(res.chats, false);
                        for (int a = 0; a < res.dialogs.size(); a++) {
                            TLRPC.TL_dialog dialog = res.dialogs.get(a);
                            if (newDialogId != 0) {
                                Integer oldNum = oldPinnedDialogNums.get(dialog.id);
                                if (oldNum != null) {
                                    dialog.pinnedNum = oldNum;
                                }
                            } else {
                                int oldIdx = oldPinnedOrder.indexOf(dialog.id);
                                int newIdx = orderArrayList.indexOf(dialog.id);
                                if (oldIdx != -1 && newIdx != -1) {
                                    if (oldIdx == newIdx) {
                                        Integer oldNum = oldPinnedDialogNums.get(dialog.id);
                                        if (oldNum != null) {
                                            dialog.pinnedNum = oldNum;
                                        }
                                    } else {
                                        long oldDid = oldPinnedOrder.get(newIdx);
                                        Integer oldNum = oldPinnedDialogNums.get(oldDid);
                                        if (oldNum != null) {
                                            dialog.pinnedNum = oldNum;
                                        }
                                    }
                                }
                            }
                            if (dialog.pinnedNum == 0) {
                                dialog.pinnedNum = (res.dialogs.size() - a) + maxPinnedNum;
                            }
                            pinnedDialogs.add(dialog.id);
                            TLRPC.TL_dialog d = dialogs_dict.get(dialog.id);

                            if (d != null) {
                                d.pinned = true;
                                d.pinnedNum = dialog.pinnedNum;
                                MessagesStorage.getInstance(currentAccount).setDialogPinned(dialog.id, dialog.pinnedNum);
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
                            dialogs.clear();
                            for (int a = 0, size = dialogs_dict.size(); a < size; a++) {
                                dialogs.add(dialogs_dict.valueAt(a));
                            }
                        }
                        sortDialogs(null);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                    }
                    MessagesStorage.getInstance(currentAccount).unpinAllDialogsExceptNew(pinnedDialogs);
                    MessagesStorage.getInstance(currentAccount).putDialogs(toCache, 1);
                    UserConfig.getInstance(currentAccount).pinnedDialogsLoaded = true;
                    UserConfig.getInstance(currentAccount).saveConfig(false);
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
        message.local_id = message.id = UserConfig.getInstance(currentAccount).getNewMessageId();
        message.date = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
        message.from_id = UserConfig.getInstance(currentAccount).getClientUserId();
        message.to_id = new TLRPC.TL_peerChannel();
        message.to_id.channel_id = chat_id;
        message.dialog_id = -chat_id;
        message.post = true;
        message.action = new TLRPC.TL_messageActionChatAddUser();
        message.action.users.add(UserConfig.getInstance(currentAccount).getClientUserId());
        if (chat.megagroup) {
            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
        }
        UserConfig.getInstance(currentAccount).saveConfig(false);

        final ArrayList<MessageObject> pushMessages = new ArrayList<>();
        final ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();

        messagesArr.add(message);
        MessageObject obj = new MessageObject(currentAccount, message, true);
        pushMessages.add(obj);

        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> NotificationsController.getInstance(currentAccount).processNewMessages(pushMessages, true, false)));
        MessagesStorage.getInstance(currentAccount).putMessages(messagesArr, true, true, false, 0);

        AndroidUtilities.runOnUIThread(() -> {
            updateInterfaceWithMessages(-chat_id, pushMessages);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                final TLRPC.TL_channels_channelParticipant res = (TLRPC.TL_channels_channelParticipant) response;
                if (res != null && res.participant instanceof TLRPC.TL_channelParticipantSelf && res.participant.inviter_id != UserConfig.getInstance(currentAccount).getClientUserId()) {
                    if (chat.megagroup && MessagesStorage.getInstance(currentAccount).isMigratedChat(chat.id)) {
                        return;
                    }
                    AndroidUtilities.runOnUIThread(() -> putUsers(res.users, false));
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, null, true, true);

                    TLRPC.TL_messageService message = new TLRPC.TL_messageService();
                    message.media_unread = true;
                    message.unread = true;
                    message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    message.post = true;
                    if (chat.megagroup) {
                        message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                    }
                    message.local_id = message.id = UserConfig.getInstance(currentAccount).getNewMessageId();
                    message.date = res.participant.date;
                    message.action = new TLRPC.TL_messageActionChatAddUser();
                    message.from_id = res.participant.inviter_id;
                    message.action.users.add(UserConfig.getInstance(currentAccount).getClientUserId());
                    message.to_id = new TLRPC.TL_peerChannel();
                    message.to_id.channel_id = chat_id;
                    message.dialog_id = -chat_id;
                    UserConfig.getInstance(currentAccount).saveConfig(false);

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

                    MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> NotificationsController.getInstance(currentAccount).processNewMessages(pushMessages, true, false)));
                    MessagesStorage.getInstance(currentAccount).putMessages(messagesArr, true, true, false, 0);

                    AndroidUtilities.runOnUIThread(() -> {
                        updateInterfaceWithMessages(-chat_id, pushMessages);
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                    });
                }
            });
        });
    }

    private int getUpdateType(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateReadMessagesContents || update instanceof TLRPC.TL_updateReadHistoryInbox ||
                update instanceof TLRPC.TL_updateReadHistoryOutbox || update instanceof TLRPC.TL_updateDeleteMessages || update instanceof TLRPC.TL_updateWebPage ||
                update instanceof TLRPC.TL_updateEditMessage) {
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
            processUpdateArray(arr, null, null, false);
        } else if (updates instanceof TLRPC.TL_updateShortChatMessage || updates instanceof TLRPC.TL_updateShortMessage) {
            final int user_id = updates instanceof TLRPC.TL_updateShortChatMessage ? updates.from_id : updates.user_id;
            TLRPC.User user = getUser(user_id);
            TLRPC.User user2 = null;
            TLRPC.User user3 = null;
            TLRPC.Chat channel = null;

            if (user == null || user.min) {
                user = MessagesStorage.getInstance(currentAccount).getUserSync(user_id);
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
                        user2 = MessagesStorage.getInstance(currentAccount).getUserSync(updates.fwd_from.from_id);
                        putUser(user2, true);
                    }
                    needFwdUser = true;
                }
                if (updates.fwd_from.channel_id != 0) {
                    channel = getChat(updates.fwd_from.channel_id);
                    if (channel == null) {
                        channel = MessagesStorage.getInstance(currentAccount).getChatSync(updates.fwd_from.channel_id);
                        putChat(channel, true);
                    }
                    needFwdUser = true;
                }
            }

            boolean needBotUser = false;
            if (updates.via_bot_id != 0) {
                user3 = getUser(updates.via_bot_id);
                if (user3 == null) {
                    user3 = MessagesStorage.getInstance(currentAccount).getUserSync(updates.via_bot_id);
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
                    chat = MessagesStorage.getInstance(currentAccount).getChatSync(updates.chat_id);
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
                            entityUser = MessagesStorage.getInstance(currentAccount).getUserSync(uid);
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
            if (user != null && user.status != null && user.status.expires <= 0) {
                onlinePrivacy.put(user.id, ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                updateStatus = true;
            }

            if (missingData) {
                needGetDiff = true;
            } else {
                if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() + updates.pts_count == updates.pts) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = updates.id;
                    int clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
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
                        value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
                        read_max.put(message.dialog_id, value);
                    }
                    message.unread = value < message.id;

                    if (message.dialog_id == clientUserId) {
                        message.unread = false;
                        message.media_unread = false;
                        message.out = true;
                    }

                    MessagesStorage.getInstance(currentAccount).setLastPtsValue(updates.pts);
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
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }
                            updateInterfaceWithMessages(user_id, objArr);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                        });
                    } else {
                        final boolean printUpdate = updatePrintingUsersWithNewMessages(-updates.chat_id, objArr);
                        if (printUpdate) {
                            updatePrintingStrings();
                        }
                        AndroidUtilities.runOnUIThread(() -> {
                            if (printUpdate) {
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }

                            updateInterfaceWithMessages(-updates.chat_id, objArr);
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
                        });
                    }
                    if (!obj.isOut()) {
                        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> NotificationsController.getInstance(currentAccount).processNewMessages(objArr, true, false)));
                    }
                    MessagesStorage.getInstance(currentAccount).putMessages(arr, false, true, false, 0);
                } else if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() != updates.pts) {
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d("need get diff short message, pts: " + MessagesStorage.getInstance(currentAccount).getLastPtsValue() + " " + updates.pts + " count = " + updates.pts_count);
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
                            TLRPC.Chat cacheChat = MessagesStorage.getInstance(currentAccount).getChatSync(updates.chat_id);
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
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(updates.users, updates.chats, true, true);
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
                        if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() + updatesNew.pts_count == updatesNew.pts) {
                            if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats, false)) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("need get diff inner TL_updates, pts: " + MessagesStorage.getInstance(currentAccount).getLastPtsValue() + " " + updates.seq);
                                }
                                needGetDiff = true;
                            } else {
                                MessagesStorage.getInstance(currentAccount).setLastPtsValue(updatesNew.pts);
                            }
                        } else if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() != updatesNew.pts) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d(update + " need get diff, pts: " + MessagesStorage.getInstance(currentAccount).getLastPtsValue() + " " + updatesNew.pts + " count = " + updatesNew.pts_count);
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
                        if (MessagesStorage.getInstance(currentAccount).getLastQtsValue() == 0 || MessagesStorage.getInstance(currentAccount).getLastQtsValue() + updatesNew.updates.size() == updatesNew.pts) {
                            processUpdateArray(updatesNew.updates, updates.users, updates.chats, false);
                            MessagesStorage.getInstance(currentAccount).setLastQtsValue(updatesNew.pts);
                            needReceivedQueue = true;
                        } else if (MessagesStorage.getInstance(currentAccount).getLastPtsValue() != updatesNew.pts) {
                            if (BuildVars.LOGS_ENABLED) {
                                FileLog.d(update + " need get diff, qts: " + MessagesStorage.getInstance(currentAccount).getLastQtsValue() + " " + updatesNew.pts);
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
                            channelPts = MessagesStorage.getInstance(currentAccount).getChannelPtsSync(channelId);
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
                                if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats, false)) {
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
                                    MessagesStorage.getInstance(currentAccount).saveChannelPts(channelId, updatesNew.pts);
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
                    processUpdate = MessagesStorage.getInstance(currentAccount).getLastSeqValue() + 1 == updates.seq_start || MessagesStorage.getInstance(currentAccount).getLastSeqValue() == updates.seq_start;
                } else {
                    processUpdate = MessagesStorage.getInstance(currentAccount).getLastSeqValue() + 1 == updates.seq || updates.seq == 0 || updates.seq == MessagesStorage.getInstance(currentAccount).getLastSeqValue();
                }
                if (processUpdate) {
                    processUpdateArray(updates.updates, updates.users, updates.chats, false);
                    if (updates.seq != 0) {
                        if (updates.date != 0) {
                            MessagesStorage.getInstance(currentAccount).setLastDateValue(updates.date);
                        }
                        MessagesStorage.getInstance(currentAccount).setLastSeqValue(updates.seq);
                    }
                } else {
                    if (BuildVars.LOGS_ENABLED) {
                        if (updates instanceof TLRPC.TL_updatesCombined) {
                            FileLog.d("need get diff TL_updatesCombined, seq: " + MessagesStorage.getInstance(currentAccount).getLastSeqValue() + " " + updates.seq_start);
                        } else {
                            FileLog.d("need get diff TL_updates, seq: " + MessagesStorage.getInstance(currentAccount).getLastSeqValue() + " " + updates.seq);
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
            MessagesStorage.getInstance(currentAccount).setLastSeqValue(updates.seq);
        } else if (updates instanceof UserActionUpdatesPts) {
            if (updates.chat_id != 0) {
                channelsPts.put(updates.chat_id, updates.pts);
                MessagesStorage.getInstance(currentAccount).saveChannelPts(updates.chat_id, updates.pts);
            } else {
                MessagesStorage.getInstance(currentAccount).setLastPtsValue(updates.pts);
            }
        }
        SecretChatHelper.getInstance(currentAccount).processPendingEncMessages();
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
            req.max_qts = MessagesStorage.getInstance(currentAccount).getLastQtsValue();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {

            });
        }
        if (updateStatus) {
            AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS));
        }
        MessagesStorage.getInstance(currentAccount).saveDiffParams(MessagesStorage.getInstance(currentAccount).getLastSeqValue(), MessagesStorage.getInstance(currentAccount).getLastPtsValue(), MessagesStorage.getInstance(currentAccount).getLastDateValue(), MessagesStorage.getInstance(currentAccount).getLastQtsValue());
    }

    public boolean processUpdateArray(ArrayList<TLRPC.Update> updates, final ArrayList<TLRPC.User> usersArr, final ArrayList<TLRPC.Chat> chatsArr, boolean fromGetDifference) {
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
        LongSparseArray<TLRPC.WebPage> webPages = null;
        ArrayList<MessageObject> pushMessages = null;
        ArrayList<TLRPC.Message> messagesArr = null;
        LongSparseArray<ArrayList<MessageObject>> editingMessages = null;
        SparseArray<SparseIntArray> channelViews = null;
        SparseLongArray markAsReadMessagesInbox = null;
        SparseLongArray markAsReadMessagesOutbox = null;
        ArrayList<Long> markAsReadMessages = null;
        SparseIntArray markAsReadEncrypted = null;
        SparseArray<ArrayList<Integer>> deletedMessages = null;
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
            if (baseUpdate instanceof TLRPC.TL_updateNewMessage || baseUpdate instanceof TLRPC.TL_updateNewChannelMessage) {
                TLRPC.Message message;
                if (baseUpdate instanceof TLRPC.TL_updateNewMessage) {
                    message = ((TLRPC.TL_updateNewMessage) baseUpdate).message;
                } else {
                    message = ((TLRPC.TL_updateNewChannelMessage) baseUpdate).message;
                    if (BuildVars.LOGS_ENABLED) {
                        FileLog.d(baseUpdate + " channelId = " + message.to_id.channel_id);
                    }
                    if (!message.out && message.from_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
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
                        chat = MessagesStorage.getInstance(currentAccount).getChatSync(chat_id);
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
                                user = MessagesStorage.getInstance(currentAccount).getUserSync(user_id);
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
                            if (a == 1 && user.status != null && user.status.expires <= 0) {
                                onlinePrivacy.put(user_id, ConnectionsManager.getInstance(currentAccount).getCurrentTime());
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
                    } else if (message.from_id == UserConfig.getInstance(currentAccount).getClientUserId() && message.action.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        continue;
                    }
                }

                if (messagesArr == null) {
                    messagesArr = new ArrayList<>();
                }
                messagesArr.add(message);
                ImageLoader.saveMessageThumbs(message);
                int clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
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

                ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                Integer value = read_max.get(message.dialog_id);
                if (value == null) {
                    value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
                    read_max.put(message.dialog_id, value);
                }
                message.unread = !(value >= message.id || chat != null && ChatObject.isNotInChat(chat) || message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate);
                if (message.dialog_id == clientUserId) {
                    message.unread = false;
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
                if (!obj.isOut() && obj.isUnread()) {
                    if (pushMessages == null) {
                        pushMessages = new ArrayList<>();
                    }
                    pushMessages.add(obj);
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
                    value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
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
                    value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, dialog_id);
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
                if (user_id != UserConfig.getInstance(currentAccount).getClientUserId()) {
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
                    onlinePrivacy.put(user_id, ConnectionsManager.getInstance(currentAccount).getCurrentTime());
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
                MessagesStorage.getInstance(currentAccount).clearUserPhotos(update.user_id);
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
            } else if (baseUpdate instanceof TLRPC.TL_updateContactRegistered) {
                TLRPC.TL_updateContactRegistered update = (TLRPC.TL_updateContactRegistered) baseUpdate;
                if (enableJoined && usersDict.containsKey(update.user_id) && !MessagesStorage.getInstance(currentAccount).isDialogHasMessages(update.user_id)) {
                    TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                    newMessage.action = new TLRPC.TL_messageActionUserJoined();
                    newMessage.local_id = newMessage.id = UserConfig.getInstance(currentAccount).getNewMessageId();
                    UserConfig.getInstance(currentAccount).saveConfig(false);
                    newMessage.unread = false;
                    newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    newMessage.date = update.date;
                    newMessage.from_id = update.user_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    newMessage.dialog_id = update.user_id;

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
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateContactLink) {
                TLRPC.TL_updateContactLink update = (TLRPC.TL_updateContactLink) baseUpdate;
                if (contactsIds == null) {
                    contactsIds = new ArrayList<>();
                }
                if (update.my_link instanceof TLRPC.TL_contactLinkContact) {
                    int idx = contactsIds.indexOf(-update.user_id);
                    if (idx != -1) {
                        contactsIds.remove(idx);
                    }
                    if (!contactsIds.contains(update.user_id)) {
                        contactsIds.add(update.user_id);
                    }
                } else {
                    int idx = contactsIds.indexOf(update.user_id);
                    if (idx != -1) {
                        contactsIds.remove(idx);
                    }
                    if (!contactsIds.contains(update.user_id)) {
                        contactsIds.add(-update.user_id);
                    }
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateNewEncryptedMessage) {
                ArrayList<TLRPC.Message> decryptedMessages = SecretChatHelper.getInstance(currentAccount).decryptMessage(((TLRPC.TL_updateNewEncryptedMessage) baseUpdate).message);
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
                    onlinePrivacy.put(encryptedChat.user_id, ConnectionsManager.getInstance(currentAccount).getCurrentTime());
                }
            } else if (baseUpdate instanceof TLRPC.TL_updateEncryptedMessagesRead) {
                TLRPC.TL_updateEncryptedMessagesRead update = (TLRPC.TL_updateEncryptedMessagesRead) baseUpdate;
                if (markAsReadEncrypted == null) {
                    markAsReadEncrypted = new SparseIntArray();
                }
                markAsReadEncrypted.put(update.chat_id, Math.max(update.max_date, update.date));
                if (tasks == null) {
                    tasks = new ArrayList<>();
                }
                tasks.add((TLRPC.TL_updateEncryptedMessagesRead) baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatParticipantAdd) {
                TLRPC.TL_updateChatParticipantAdd update = (TLRPC.TL_updateChatParticipantAdd) baseUpdate;
                MessagesStorage.getInstance(currentAccount).updateChatInfo(update.chat_id, update.user_id, 0, update.inviter_id, update.version);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatParticipantDelete) {
                TLRPC.TL_updateChatParticipantDelete update = (TLRPC.TL_updateChatParticipantDelete) baseUpdate;
                MessagesStorage.getInstance(currentAccount).updateChatInfo(update.chat_id, update.user_id, 1, 0, update.version);
            } else if (baseUpdate instanceof TLRPC.TL_updateDcOptions || baseUpdate instanceof TLRPC.TL_updateConfig) {
                ConnectionsManager.getInstance(currentAccount).updateDcSettings();
            } else if (baseUpdate instanceof TLRPC.TL_updateEncryption) {
                SecretChatHelper.getInstance(currentAccount).processUpdateEncryption((TLRPC.TL_updateEncryption) baseUpdate, usersDict);
            } else if (baseUpdate instanceof TLRPC.TL_updateUserBlocked) {
                final TLRPC.TL_updateUserBlocked finalUpdate = (TLRPC.TL_updateUserBlocked) baseUpdate;
                if (finalUpdate.blocked) {
                    SparseIntArray ids = new SparseIntArray();
                    ids.put(finalUpdate.user_id, 1);
                    MessagesStorage.getInstance(currentAccount).putBlockedUsers(ids, false);
                } else {
                    MessagesStorage.getInstance(currentAccount).deleteBlockedUser(finalUpdate.user_id);
                }
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
                    if (finalUpdate.blocked) {
                        if (blockedUsers.indexOfKey(finalUpdate.user_id) < 0) {
                            blockedUsers.put(finalUpdate.user_id, 1);
                        }
                    } else {
                        blockedUsers.delete(finalUpdate.user_id);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.blockedUsersDidLoaded);
                }));
            } else if (baseUpdate instanceof TLRPC.TL_updateNotifySettings) {
                if (updatesOnMainThread == null) {
                    updatesOnMainThread = new ArrayList<>();
                }
                updatesOnMainThread.add(baseUpdate);
            } else if (baseUpdate instanceof TLRPC.TL_updateServiceNotification) {
                final TLRPC.TL_updateServiceNotification update = (TLRPC.TL_updateServiceNotification) baseUpdate;
                if (update.popup && update.message != null && update.message.length() > 0) {
                    AndroidUtilities.runOnUIThread(() -> NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needShowAlert, 2, update.message, update.type));
                }
                if ((update.flags & 2) != 0) {
                    TLRPC.TL_message newMessage = new TLRPC.TL_message();
                    newMessage.local_id = newMessage.id = UserConfig.getInstance(currentAccount).getNewMessageId();
                    UserConfig.getInstance(currentAccount).saveConfig(false);
                    newMessage.unread = true;
                    newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    if (update.inbox_date != 0) {
                        newMessage.date = update.inbox_date;
                    } else {
                        newMessage.date = (int) (System.currentTimeMillis() / 1000);
                    }
                    newMessage.from_id = 777000;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.getInstance(currentAccount).getClientUserId();
                    newMessage.dialog_id = 777000;
                    if (update.media != null) {
                        newMessage.media = update.media;
                        newMessage.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                    }
                    newMessage.message = update.message;
                    if (update.entities != null) {
                        newMessage.entities = update.entities;
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
                    channelPts = MessagesStorage.getInstance(currentAccount).getChannelPtsSync(update.channel_id);
                    if (channelPts == 0) {
                        TLRPC.Chat chat = chatsDict.get(update.channel_id);
                        if (chat == null || chat.min) {
                            chat = getChat(update.channel_id);
                        }
                        if (chat == null || chat.min) {
                            chat = MessagesStorage.getInstance(currentAccount).getChatSync(update.channel_id);
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
                    value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(false, dialog_id);
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
                    value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(true, dialog_id);
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
                MessagesStorage.getInstance(currentAccount).updateChatInfo(update.chat_id, update.user_id, 2, update.is_admin ? 1 : 0, update.version);
            } else if (baseUpdate instanceof TLRPC.TL_updateChatAdmins) {
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
                int clientUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                if (baseUpdate instanceof TLRPC.TL_updateEditChannelMessage) {
                    message = ((TLRPC.TL_updateEditChannelMessage) baseUpdate).message;
                    TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                    if (chat == null) {
                        chat = getChat(message.to_id.channel_id);
                    }
                    if (chat == null) {
                        chat = MessagesStorage.getInstance(currentAccount).getChatSync(message.to_id.channel_id);
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
                if (!message.out && message.from_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
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
                                user = MessagesStorage.getInstance(currentAccount).getUserSync(user_id);
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
                    if (message.to_id.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                        message.to_id.user_id = message.from_id;
                    }
                    message.dialog_id = message.to_id.user_id;
                }

                ConcurrentHashMap<Long, Integer> read_max = message.out ? dialogs_read_outbox_max : dialogs_read_inbox_max;
                Integer value = read_max.get(message.dialog_id);
                if (value == null) {
                    value = MessagesStorage.getInstance(currentAccount).getDialogReadMax(message.out, message.dialog_id);
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

                if (editingMessages == null) {
                    editingMessages = new LongSparseArray<>();
                }
                ArrayList<MessageObject> arr = editingMessages.get(message.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<>();
                    editingMessages.put(message.dialog_id, arr);
                }
                arr.add(obj);
            } else if (baseUpdate instanceof TLRPC.TL_updateChannelPinnedMessage) {
                TLRPC.TL_updateChannelPinnedMessage update = (TLRPC.TL_updateChannelPinnedMessage) baseUpdate;
                if (BuildVars.LOGS_ENABLED) {
                    FileLog.d(baseUpdate + " channelId = " + update.channel_id);
                }
                MessagesStorage.getInstance(currentAccount).updateChannelPinnedMessage(update.channel_id, update.id);
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
                LocaleController.getInstance().saveRemoteLocaleStrings(update.difference, currentAccount);
            } else if (baseUpdate instanceof TLRPC.TL_updateLangPackTooLong) {
                LocaleController.getInstance().reloadCurrentRemoteLocale(currentAccount);
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
            ContactsController.getInstance(currentAccount).processContactsUpdates(contactsIds, usersDict);
        }

        if (pushMessages != null) {
            final ArrayList<MessageObject> pushMessagesFinal = pushMessages;
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> NotificationsController.getInstance(currentAccount).processNewMessages(pushMessagesFinal, true, false)));
        }

        if (messagesArr != null) {
            StatsController.getInstance(currentAccount).incrementReceivedItemsCount(ConnectionsManager.getCurrentNetworkType(), StatsController.TYPE_MESSAGES, messagesArr.size());
            MessagesStorage.getInstance(currentAccount).putMessages(messagesArr, true, true, false, DownloadController.getInstance(currentAccount).getAutodownloadMask());
        }
        if (editingMessages != null) {
            for (int b = 0, size = editingMessages.size(); b < size; b++) {
                TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                ArrayList<MessageObject> messageObjects = editingMessages.valueAt(b);
                for (int a = 0, size2 = messageObjects.size(); a < size2; a++) {
                    messagesRes.messages.add(messageObjects.get(a).messageOwner);
                }
                MessagesStorage.getInstance(currentAccount).putMessages(messagesRes, editingMessages.keyAt(b), -2, 0, false);
            }
        }

        if (channelViews != null) {
            MessagesStorage.getInstance(currentAccount).putChannelViews(channelViews, true);
        }

        final LongSparseArray<ArrayList<MessageObject>> editingMessagesFinal = editingMessages;
        final SparseArray<SparseIntArray> channelViewsFinal = channelViews;
        final LongSparseArray<TLRPC.WebPage> webPagesFinal = webPages;
        final LongSparseArray<ArrayList<MessageObject>> messagesFinal = messages;
        final ArrayList<TLRPC.ChatParticipants> chatInfoToUpdateFinal = chatInfoToUpdate;
        final ArrayList<Integer> contactsIdsFinal = contactsIds;
        final ArrayList<TLRPC.Update> updatesOnMainThreadFinal = updatesOnMainThread;
        AndroidUtilities.runOnUIThread(() -> {
            int updateMask = interfaceUpdateMaskFinal;
            boolean hasDraftUpdates = false;

            if (updatesOnMainThreadFinal != null) {
                ArrayList<TLRPC.User> dbUsers = new ArrayList<>();
                ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<>();
                SharedPreferences.Editor editor = null;
                for (int a = 0, size = updatesOnMainThreadFinal.size(); a < size; a++) {
                    final TLRPC.Update baseUpdate = updatesOnMainThreadFinal.get(a);
                    if (baseUpdate instanceof TLRPC.TL_updatePrivacy) {
                        TLRPC.TL_updatePrivacy update = (TLRPC.TL_updatePrivacy) baseUpdate;
                        if (update.key instanceof TLRPC.TL_privacyKeyStatusTimestamp) {
                            ContactsController.getInstance(currentAccount).setPrivacyRules(update.rules, 0);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyChatInvite) {
                            ContactsController.getInstance(currentAccount).setPrivacyRules(update.rules, 1);
                        } else if (update.key instanceof TLRPC.TL_privacyKeyPhoneCall) {
                            ContactsController.getInstance(currentAccount).setPrivacyRules(update.rules, 2);
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
                        final TLRPC.User toDbUser = new TLRPC.TL_user(); //TODO remove
                        toDbUser.id = update.user_id;
                        toDbUser.status = update.status;
                        dbUsersStatus.add(toDbUser);
                        if (update.user_id == UserConfig.getInstance(currentAccount).getClientUserId()) {
                            NotificationsController.getInstance(currentAccount).setLastOnlineFromOtherDevice(update.status.expires);
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
                        final TLRPC.User toDbUser = new TLRPC.TL_user(); //TODO remove
                        toDbUser.id = update.user_id;
                        toDbUser.first_name = update.first_name;
                        toDbUser.last_name = update.last_name;
                        toDbUser.username = update.username;
                        dbUsers.add(toDbUser);
                    } else if (baseUpdate instanceof TLRPC.TL_updateDialogPinned) {
                        TLRPC.TL_updateDialogPinned updateDialogPinned = (TLRPC.TL_updateDialogPinned) baseUpdate;
                        long did;
                        if (updateDialogPinned.peer instanceof TLRPC.TL_dialogPeer) {
                            TLRPC.Peer peer = ((TLRPC.TL_dialogPeer) updateDialogPinned.peer).peer;
                            if (peer instanceof TLRPC.TL_peerUser) {
                                did = peer.user_id;
                            } else if (peer instanceof TLRPC.TL_peerChat) {
                                did = -peer.chat_id;
                            } else {
                                did = -peer.channel_id;
                            }
                        } else {
                            did = 0;
                        }
                        if (!pinDialog(did, updateDialogPinned.pinned, null, -1)) {
                            UserConfig.getInstance(currentAccount).pinnedDialogsLoaded = false;
                            UserConfig.getInstance(currentAccount).saveConfig(false);
                            loadPinnedDialogs(did, null);
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updatePinnedDialogs) {
                        TLRPC.TL_updatePinnedDialogs update = (TLRPC.TL_updatePinnedDialogs) baseUpdate;
                        UserConfig.getInstance(currentAccount).pinnedDialogsLoaded = false;
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                        ArrayList<Long> order;
                        if ((update.flags & 1) != 0) {
                            order = new ArrayList<>();
                            ArrayList<TLRPC.DialogPeer> peers = ((TLRPC.TL_updatePinnedDialogs) baseUpdate).order;
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
                        loadPinnedDialogs(0, order);
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserPhoto) {
                        TLRPC.TL_updateUserPhoto update = (TLRPC.TL_updateUserPhoto) baseUpdate;
                        final TLRPC.User currentUser = getUser(update.user_id);
                        if (currentUser != null) {
                            currentUser.photo = update.photo;
                        }
                        final TLRPC.User toDbUser = new TLRPC.TL_user(); //TODO remove
                        toDbUser.id = update.user_id;
                        toDbUser.photo = update.photo;
                        dbUsers.add(toDbUser);
                    } else if (baseUpdate instanceof TLRPC.TL_updateUserPhone) {
                        TLRPC.TL_updateUserPhone update = (TLRPC.TL_updateUserPhone) baseUpdate;
                        final TLRPC.User currentUser = getUser(update.user_id);
                        if (currentUser != null) {
                            currentUser.phone = update.phone;
                            Utilities.phoneBookQueue.postRunnable(() -> ContactsController.getInstance(currentAccount).addContactToPhoneBook(currentUser, true));
                        }
                        final TLRPC.User toDbUser = new TLRPC.TL_user(); //TODO remove
                        toDbUser.id = update.user_id;
                        toDbUser.phone = update.phone;
                        dbUsers.add(toDbUser);
                    } else if (baseUpdate instanceof TLRPC.TL_updateNotifySettings) {
                        TLRPC.TL_updateNotifySettings update = (TLRPC.TL_updateNotifySettings) baseUpdate;
                        if (update.notify_settings instanceof TLRPC.TL_peerNotifySettings) {
                            if (editor == null) {
                                editor = notificationsPreferences.edit();
                            }
                            int currentTime1 = ConnectionsManager.getInstance(currentAccount).getCurrentTime();
                            if (update.peer instanceof TLRPC.TL_notifyPeer) {
                                long dialog_id;
                                if (update.peer.peer.user_id != 0) {
                                    dialog_id = update.peer.peer.user_id;
                                } else if (update.peer.peer.chat_id != 0) {
                                    dialog_id = -update.peer.peer.chat_id;
                                } else {
                                    dialog_id = -update.peer.peer.channel_id;
                                }
                                TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
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
                                        MessagesStorage.getInstance(currentAccount).setDialogFlags(dialog_id, ((long) until << 32) | 1);
                                        NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(dialog_id);
                                    } else {
                                        if (dialog != null) {
                                            update.notify_settings.mute_until = 0;
                                        }
                                        editor.putInt("notify2_" + dialog_id, 0);
                                        MessagesStorage.getInstance(currentAccount).setDialogFlags(dialog_id, 0);
                                    }
                                } else {
                                    if (dialog != null) {
                                        update.notify_settings.mute_until = 0;
                                    }
                                    editor.remove("notify2_" + dialog_id);
                                    MessagesStorage.getInstance(currentAccount).setDialogFlags(dialog_id, 0);
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
                                    editor.putBoolean("EnableGroup", update.notify_settings.mute_until < currentTime1);
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
                                    editor.putBoolean("EnableAll", update.notify_settings.mute_until < currentTime1);
                                }
                            }
                        }
                    } else if (baseUpdate instanceof TLRPC.TL_updateChannel) {
                        final TLRPC.TL_updateChannel update = (TLRPC.TL_updateChannel) baseUpdate;
                        TLRPC.TL_dialog dialog = dialogs_dict.get(-(long) update.channel_id);
                        TLRPC.Chat chat = getChat(update.channel_id);
                        if (chat != null) {
                            if (dialog == null && chat instanceof TLRPC.TL_channel && !chat.left) {
                                Utilities.stageQueue.postRunnable(() -> getChannelDifference(update.channel_id, 1, 0, null));
                            } else if (chat.left && dialog != null && (proxyDialog == null || proxyDialog.id != dialog.id)) {
                                deleteDialog(dialog.id, 0);
                            }
                        }
                        updateMask |= UPDATE_MASK_CHANNEL;
                        loadFullChat(update.channel_id, 0, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateChatAdmins) {
                        updateMask |= UPDATE_MASK_CHAT_ADMINS;
                    } else if (baseUpdate instanceof TLRPC.TL_updateStickerSets) {
                        TLRPC.TL_updateStickerSets update = (TLRPC.TL_updateStickerSets) baseUpdate;
                        DataQuery.getInstance(currentAccount).loadStickers(DataQuery.TYPE_IMAGE, false, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateStickerSetsOrder) {
                        TLRPC.TL_updateStickerSetsOrder update = (TLRPC.TL_updateStickerSetsOrder) baseUpdate;
                        DataQuery.getInstance(currentAccount).reorderStickers(update.masks ? DataQuery.TYPE_MASK : DataQuery.TYPE_IMAGE, ((TLRPC.TL_updateStickerSetsOrder) baseUpdate).order);
                    } else if (baseUpdate instanceof TLRPC.TL_updateFavedStickers) {
                        DataQuery.getInstance(currentAccount).loadRecents(DataQuery.TYPE_FAVE, false, false, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateContactsReset) {
                        ContactsController.getInstance(currentAccount).forceImportContacts();
                    } else if (baseUpdate instanceof TLRPC.TL_updateNewStickerSet) {
                        TLRPC.TL_updateNewStickerSet update = (TLRPC.TL_updateNewStickerSet) baseUpdate;
                        DataQuery.getInstance(currentAccount).addNewStickerSet(update.stickerset);
                    } else if (baseUpdate instanceof TLRPC.TL_updateSavedGifs) {
                        SharedPreferences.Editor editor2 = emojiPreferences.edit();
                        editor2.putLong("lastGifLoadTime", 0).commit();
                    } else if (baseUpdate instanceof TLRPC.TL_updateRecentStickers) {
                        SharedPreferences.Editor editor2 = emojiPreferences.edit();
                        editor2.putLong("lastStickersLoadTime", 0).commit();
                    } else if (baseUpdate instanceof TLRPC.TL_updateDraftMessage) {
                        TLRPC.TL_updateDraftMessage update = (TLRPC.TL_updateDraftMessage) baseUpdate;
                        hasDraftUpdates = true;
                        long did;
                        TLRPC.Peer peer = ((TLRPC.TL_updateDraftMessage) baseUpdate).peer;
                        if (peer.user_id != 0) {
                            did = peer.user_id;
                        } else if (peer.channel_id != 0) {
                            did = -peer.channel_id;
                        } else {
                            did = -peer.chat_id;
                        }
                        DataQuery.getInstance(currentAccount).saveDraft(did, update.draft, null, true);
                    } else if (baseUpdate instanceof TLRPC.TL_updateReadFeaturedStickers) {
                        DataQuery.getInstance(currentAccount).markFaturedStickersAsRead(false);
                    } else if (baseUpdate instanceof TLRPC.TL_updatePhoneCall) {
                        TLRPC.TL_updatePhoneCall upd = (TLRPC.TL_updatePhoneCall) baseUpdate;
                        TLRPC.PhoneCall call = upd.phone_call;
                        VoIPService svc = VoIPService.getSharedInstance();
                        if (BuildVars.LOGS_ENABLED) {
                            FileLog.d("Received call in update: " + call);
                            FileLog.d("call id " + call.id);
                        }
                        if (call instanceof TLRPC.TL_phoneCallRequested) {
                            if (call.date + callRingTimeout / 1000 < ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("ignoring too old call");
                                }
                                continue;
                            }
                            TelephonyManager tm = (TelephonyManager) ApplicationLoader.applicationContext.getSystemService(Context.TELEPHONY_SERVICE);
                            if (svc != null || VoIPService.callIShouldHavePutIntoIntent!=null || tm.getCallState() != TelephonyManager.CALL_STATE_IDLE) {
                                if (BuildVars.LOGS_ENABLED) {
                                    FileLog.d("Auto-declining call " + call.id + " because there's already active one");
                                }
                                TLRPC.TL_phone_discardCall req = new TLRPC.TL_phone_discardCall();
                                req.peer = new TLRPC.TL_inputPhoneCall();
                                req.peer.access_hash = call.access_hash;
                                req.peer.id = call.id;
                                req.reason = new TLRPC.TL_phoneCallDiscardReasonBusy();
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
                            intent.putExtra("user_id", call.participant_id == UserConfig.getInstance(currentAccount).getClientUserId() ? call.admin_id : call.participant_id);
                            intent.putExtra("account", currentAccount);
                            try {
                                if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){
                                    ApplicationLoader.applicationContext.startForegroundService(intent);
                                }else{
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
                        MessagesStorage.getInstance(currentAccount).setDialogUnread(did, update.unread);
                        TLRPC.TL_dialog dialog = dialogs_dict.get(did);
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
                    } else if (baseUpdate instanceof TLRPC.TL_updateGroupCall) {

                    } else if (baseUpdate instanceof TLRPC.TL_updateGroupCallParticipant) {

                    }
                }
                if (editor != null) {
                    editor.commit();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                }
                MessagesStorage.getInstance(currentAccount).updateUsers(dbUsersStatus, true, true, true);
                MessagesStorage.getInstance(currentAccount).updateUsers(dbUsers, false, true, true);
            }

            if (webPagesFinal != null) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didReceivedWebpagesInUpdates, webPagesFinal);
                for (int b = 0, size = webPagesFinal.size(); b < size; b++) {
                    long key = webPagesFinal.keyAt(b);
                    ArrayList<MessageObject> arrayList = reloadingWebpagesPending.get(key);
                    reloadingWebpagesPending.remove(key);
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
                            reloadingWebpagesPending.put(webpage.id, arrayList);
                        }
                        if (!arr.isEmpty()) {
                            MessagesStorage.getInstance(currentAccount).putMessages(arr, true, true, false, DownloadController.getInstance(currentAccount).getAutodownloadMask());
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList);
                        }
                    }
                }
            }

            boolean updateDialogs = false;
            if (messagesFinal != null) {
                for (int a = 0, size = messagesFinal.size(); a < size; a++) {
                    long key = messagesFinal.keyAt(a);
                    ArrayList<MessageObject> value = messagesFinal.valueAt(a);
                    updateInterfaceWithMessages(key, value);
                }
                updateDialogs = true;
            } else if (hasDraftUpdates) {
                sortDialogs(null);
                updateDialogs = true;
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
                    DataQuery.getInstance(currentAccount).loadReplyMessagesForMessages(arrayList, dialog_id);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList);
                }
            }
            if (updateDialogs) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
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
                    MessagesStorage.getInstance(currentAccount).updateChatParticipants(info);
                }
            }
            if (channelViewsFinal != null) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didUpdatedMessagesViews, channelViewsFinal);
            }
            if (updateMask != 0) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, updateMask);
            }
        });

        final SparseLongArray markAsReadMessagesInboxFinal = markAsReadMessagesInbox;
        final SparseLongArray markAsReadMessagesOutboxFinal = markAsReadMessagesOutbox;
        final ArrayList<Long> markAsReadMessagesFinal = markAsReadMessages;
        final SparseIntArray markAsReadEncryptedFinal = markAsReadEncrypted;
        final SparseArray<ArrayList<Integer>> deletedMessagesFinal = deletedMessages;
        final SparseIntArray clearHistoryMessagesFinal = clearHistoryMessages;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> AndroidUtilities.runOnUIThread(() -> {
            int updateMask = 0;
            if (markAsReadMessagesInboxFinal != null || markAsReadMessagesOutboxFinal != null) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesRead, markAsReadMessagesInboxFinal, markAsReadMessagesOutboxFinal);
                if (markAsReadMessagesInboxFinal != null) {
                    NotificationsController.getInstance(currentAccount).processReadMessages(markAsReadMessagesInboxFinal, 0, 0, 0, false);
                    SharedPreferences.Editor editor = notificationsPreferences.edit();
                    for (int b = 0, size = markAsReadMessagesInboxFinal.size(); b < size; b++) {
                        int key = markAsReadMessagesInboxFinal.keyAt(b);
                        int messageId = (int) markAsReadMessagesInboxFinal.valueAt(b);
                        TLRPC.TL_dialog dialog = dialogs_dict.get((long) key);
                        if (dialog != null && dialog.top_message > 0 && dialog.top_message <= messageId) {
                            MessageObject obj = dialogMessage.get(dialog.id);
                            if (obj != null && !obj.isOut()) {
                                obj.setIsRead();
                                updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                            }
                        }
                        if (key != UserConfig.getInstance(currentAccount).getClientUserId()) {
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
                        TLRPC.TL_dialog dialog = dialogs_dict.get((long) key);
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
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesReadEncrypted, key, value);
                    long dialog_id = (long) (key) << 32;
                    TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
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
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesReadContent, markAsReadMessagesFinal);
            }
            if (deletedMessagesFinal != null) {
                for (int a = 0, size = deletedMessagesFinal.size(); a < size; a++) {
                    int key = deletedMessagesFinal.keyAt(a);
                    ArrayList<Integer> arrayList = deletedMessagesFinal.valueAt(a);
                    if (arrayList == null) {
                        continue;
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.messagesDeleted, arrayList, key);
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
                NotificationsController.getInstance(currentAccount).removeDeletedMessagesFromNotifications(deletedMessagesFinal);
            }
            if (clearHistoryMessagesFinal != null) {
                for (int a = 0, size = clearHistoryMessagesFinal.size(); a < size; a++) {
                    int key = clearHistoryMessagesFinal.keyAt(a);
                    int id = clearHistoryMessagesFinal.valueAt(a);
                    long did = (long) -key;
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.historyCleared, did, id);
                    MessageObject obj = dialogMessage.get(did);
                    if (obj != null) {
                        if (obj.getId() <= id) {
                            obj.deleted = true;
                            break;
                        }
                    }
                }
                NotificationsController.getInstance(currentAccount).removeDeletedHisoryFromNotifications(clearHistoryMessagesFinal);
            }
            if (updateMask != 0) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.updateInterfaces, updateMask);
            }
        }));

        if (webPages != null) {
            MessagesStorage.getInstance(currentAccount).putWebPages(webPages);
        }
        if (markAsReadMessagesInbox != null || markAsReadMessagesOutbox != null || markAsReadEncrypted != null || markAsReadMessages != null) {
            if (markAsReadMessagesInbox != null || markAsReadMessages != null) {
                MessagesStorage.getInstance(currentAccount).updateDialogsWithReadMessages(markAsReadMessagesInbox, markAsReadMessagesOutbox, markAsReadMessages, true);
            }
            MessagesStorage.getInstance(currentAccount).markMessagesAsRead(markAsReadMessagesInbox, markAsReadMessagesOutbox, markAsReadEncrypted, true);
        }
        if (markAsReadMessages != null) {
            MessagesStorage.getInstance(currentAccount).markMessagesContentAsRead(markAsReadMessages, ConnectionsManager.getInstance(currentAccount).getCurrentTime());
        }
        if (deletedMessages != null) {
            for (int a = 0, size = deletedMessages.size(); a < size; a++) {
                final int key = deletedMessages.keyAt(a);
                final ArrayList<Integer> arrayList = deletedMessages.valueAt(a);
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = MessagesStorage.getInstance(currentAccount).markMessagesAsDeleted(arrayList, false, key);
                    MessagesStorage.getInstance(currentAccount).updateDialogsWithDeletedMessages(arrayList, dialogIds, false, key);
                });
            }
        }
        if (clearHistoryMessages != null) {
            for (int a = 0, size = clearHistoryMessages.size(); a < size; a++) {
                final int key = clearHistoryMessages.keyAt(a);
                final int id = clearHistoryMessages.valueAt(a);
                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(() -> {
                    ArrayList<Long> dialogIds = MessagesStorage.getInstance(currentAccount).markMessagesAsDeleted(key, id, false);
                    MessagesStorage.getInstance(currentAccount).updateDialogsWithDeletedMessages(new ArrayList<>(), dialogIds, false, key);
                });
            }
        }
        if (tasks != null) {
            for (int a = 0, size = tasks.size(); a < size; a++) {
                TLRPC.TL_updateEncryptedMessagesRead update = tasks.get(a);
                MessagesStorage.getInstance(currentAccount).createTaskForSecretChat(update.chat_id, update.max_date, update.date, 1, null);
            }
        }

        return true;
    }

    public boolean isDialogMuted(long dialog_id) {
        int mute_type = notificationsPreferences.getInt("notify2_" + dialog_id, -1);
        if (mute_type == -1) {
            if ((int) dialog_id < 0) {
                if (!notificationsPreferences.getBoolean("EnableGroup", true)) {
                    return true;
                }
            } else {
                if (!notificationsPreferences.getBoolean("EnableAll", true)) {
                    return true;
                }
            }
        }
        if (mute_type == 2) {
            return true;
        } else if (mute_type == 3) {
            int mute_until = notificationsPreferences.getInt("notifyuntil_" + dialog_id, 0);
            if (mute_until >= ConnectionsManager.getInstance(currentAccount).getCurrentTime()) {
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

    protected void updateInterfaceWithMessages(long uid, ArrayList<MessageObject> messages) {
        updateInterfaceWithMessages(uid, messages, false);
    }

    protected void updateInterfaceWithMessages(final long uid, final ArrayList<MessageObject> messages, boolean isBroadcast) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        boolean isEncryptedChat = ((int) uid) == 0;
        MessageObject lastMessage = null;
        int channelId = 0;
        boolean updateRating = false;
        for (int a = 0; a < messages.size(); a++) {
            MessageObject message = messages.get(a);
            if (lastMessage == null || (!isEncryptedChat && message.getId() > lastMessage.getId() || (isEncryptedChat || message.getId() < 0 && lastMessage.getId() < 0) && message.getId() < lastMessage.getId()) || message.messageOwner.date > lastMessage.messageOwner.date) {
                lastMessage = message;
                if (message.messageOwner.to_id.channel_id != 0) {
                    channelId = message.messageOwner.to_id.channel_id;
                }
            }
            if (message.isOut() && !message.isSending() && !message.isForwarded()) {
                if (message.isNewGif()) {
                    DataQuery.getInstance(currentAccount).addRecentGif(message.messageOwner.media.document, message.messageOwner.date);
                } else if (message.isSticker()) {
                    DataQuery.getInstance(currentAccount).addRecentSticker(DataQuery.TYPE_IMAGE, message.messageOwner.media.document, message.messageOwner.date, false);
                }
            }
            if (message.isOut() && message.isSent()) {
                updateRating = true;
            }
        }
        DataQuery.getInstance(currentAccount).loadReplyMessagesForMessages(messages, uid);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didReceivedNewMessages, uid, messages);

        if (lastMessage == null) {
            return;
        }
        TLRPC.TL_dialog dialog = dialogs_dict.get(uid);
        if (lastMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            if (dialog != null) {
                dialogs.remove(dialog);
                dialogsServerOnly.remove(dialog);
                dialogsGroupsOnly.remove(dialog);
                dialogs_dict.remove(dialog.id);
                dialogs_read_inbox_max.remove(dialog.id);
                dialogs_read_outbox_max.remove(dialog.id);
                nextDialogsCacheOffset--;
                dialogMessage.remove(dialog.id);
                MessageObject object = dialogMessagesByIds.get(dialog.top_message);
                dialogMessagesByIds.remove(dialog.top_message);
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = 0;
                NotificationsController.getInstance(currentAccount).removeNotificationsForDialog(dialog.id);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
            }
            return;
        }

        boolean changed = false;

        if (dialog == null) {
            if (!isBroadcast) {
                TLRPC.Chat chat = getChat(channelId);
                if (channelId != 0 && chat == null || chat != null && chat.left) {
                    return;
                }
                dialog = new TLRPC.TL_dialog();
                dialog.id = uid;
                dialog.unread_count = 0;
                dialog.top_message = lastMessage.getId();
                dialog.last_message_date = lastMessage.messageOwner.date;
                dialog.flags = ChatObject.isChannel(chat) ? 1 : 0;
                dialogs_dict.put(uid, dialog);
                dialogs.add(dialog);
                dialogMessage.put(uid, lastMessage);
                if (lastMessage.messageOwner.to_id.channel_id == 0) {
                    dialogMessagesByIds.put(lastMessage.getId(), lastMessage);
                    if (lastMessage.messageOwner.random_id != 0) {
                        dialogMessagesByRandomIds.put(lastMessage.messageOwner.random_id, lastMessage);
                    }
                }
                nextDialogsCacheOffset++;
                changed = true;
            }
        } else {
            if ((dialog.top_message > 0 && lastMessage.getId() > 0 && lastMessage.getId() > dialog.top_message) ||
                    (dialog.top_message < 0 && lastMessage.getId() < 0 && lastMessage.getId() < dialog.top_message) ||
                    dialogMessage.indexOfKey(uid) < 0 || dialog.top_message < 0 || dialog.last_message_date <= lastMessage.messageOwner.date) {
                MessageObject object = dialogMessagesByIds.get(dialog.top_message);
                dialogMessagesByIds.remove(dialog.top_message);
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = lastMessage.getId();
                if (!isBroadcast) {
                    dialog.last_message_date = lastMessage.messageOwner.date;
                    changed = true;
                }
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
            DataQuery.getInstance(currentAccount).increasePeerRaiting(uid);
        }
    }

    public void sortDialogs(SparseArray<TLRPC.Chat> chatsDict) {
        dialogsServerOnly.clear();
        dialogsGroupsOnly.clear();
        dialogsForward.clear();
        unreadUnmutedDialogs = 0;
        boolean selfAdded = false;
        int selfId = UserConfig.getInstance(currentAccount).getClientUserId();
        Collections.sort(dialogs, dialogComparator);
        isLeftProxyChannel = true;
        if (proxyDialog != null && proxyDialog.id < 0) {
            TLRPC.Chat chat = getChat(-(int) proxyDialog.id);
            if (chat != null && !chat.left) {
                isLeftProxyChannel = false;
            }
        }
        for (int a = 0; a < dialogs.size(); a++) {
            TLRPC.TL_dialog d = dialogs.get(a);
            int high_id = (int) (d.id >> 32);
            int lower_id = (int) d.id;
            if (lower_id == selfId) {
                dialogsForward.add(0, d);
                selfAdded = true;
            } else {
                dialogsForward.add(d);
            }
            if (lower_id != 0 && high_id != 1) {
                dialogsServerOnly.add(d);
                if (DialogObject.isChannel(d)) {
                    TLRPC.Chat chat = getChat(-lower_id);
                    if (chat != null && (chat.megagroup && (chat.admin_rights != null && (chat.admin_rights.post_messages || chat.admin_rights.add_admins)) || chat.creator)) {
                        dialogsGroupsOnly.add(d);
                    }
                } else if (lower_id < 0) {
                    if (chatsDict != null) {
                        TLRPC.Chat chat = chatsDict.get(-lower_id);
                        if (chat != null && chat.migrated_to != null) {
                            dialogs.remove(a);
                            a--;
                            continue;
                        }
                    }
                    dialogsGroupsOnly.add(d);
                }
            }
            if (proxyDialog != null && d.id == proxyDialog.id && isLeftProxyChannel) {
                dialogs.remove(a);
                a--;
            }
            if ((d.unread_count != 0 || d.unread_mark) && !isDialogMuted(d.id)) {
                unreadUnmutedDialogs++;
            }
        }
        if (proxyDialog != null && isLeftProxyChannel) {
            dialogs.add(0, proxyDialog);
        }
        if (!selfAdded) {
            TLRPC.User user = UserConfig.getInstance(currentAccount).getCurrentUser();
            if (user != null) {
                TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
                dialog.id = user.id;
                dialog.notify_settings = new TLRPC.TL_peerNotifySettings();
                dialog.peer = new TLRPC.TL_peerUser();
                dialog.peer.user_id = user.id;
                dialogsForward.add(0, dialog);
            }
        }
    }

    private static String getRestrictionReason(String reason) {
        if (reason == null || reason.length() == 0) {
            return null;
        }
        int index = reason.indexOf(": ");
        if (index > 0) {
            String type = reason.substring(0, index);
            if (type.contains("-all") || type.contains("-android")) {
                return reason.substring(index + 2);
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
                final AlertDialog progressDialog = new AlertDialog(fragment.getParentActivity(), 1);
                progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
                progressDialog.setCanceledOnTouchOutside(false);
                progressDialog.setCancelable(false);
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
                final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
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
                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                            fragment.presentFragment(new ChatActivity(bundle), true);
                        });
                    }
                });
                progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                    try {
                        dialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
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
            final AlertDialog progressDialog[] = new AlertDialog[] {new AlertDialog(fragment.getParentActivity(), 1)};

            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            final int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
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
                    MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, false, true);
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
                progressDialog[0].setMessage(LocaleController.getString("Loading", R.string.Loading));
                progressDialog[0].setCanceledOnTouchOutside(false);
                progressDialog[0].setCancelable(false);
                progressDialog[0].setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), (dialog, which) -> {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
                    try {
                        dialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                });
                fragment.showDialog(progressDialog[0]);
            }, 500);
        }
    }
}
