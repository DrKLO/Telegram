/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Bundle;
import android.util.Base64;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.messenger.query.BotQuery;
import org.telegram.messenger.query.MessagesQuery;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ChatActivity;
import org.telegram.ui.Components.AlertsCreator;
import org.telegram.ui.ProfileActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class MessagesController implements NotificationCenter.NotificationCenterDelegate {

    private ConcurrentHashMap<Integer, TLRPC.Chat> chats = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.EncryptedChat> encryptedChats = new ConcurrentHashMap<>(10, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.User> users = new ConcurrentHashMap<>(100, 1.0f, 2);
    private ConcurrentHashMap<String, TLRPC.User> usersByUsernames = new ConcurrentHashMap<>(100, 1.0f, 2);

    private ArrayList<Integer> joiningToChannels = new ArrayList<>();

    private HashMap<Integer, TLRPC.ExportedChatInvite> exportedChats = new HashMap<>();

    public ArrayList<TLRPC.Dialog> dialogs = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsServerOnly = new ArrayList<>();
    public ArrayList<TLRPC.Dialog> dialogsGroupsOnly = new ArrayList<>();
    public int nextDialogsCacheOffset;
    public ConcurrentHashMap<Long, Integer> dialogs_read_inbox_max = new ConcurrentHashMap<>(100, 1.0f, 2);
    public ConcurrentHashMap<Long, TLRPC.Dialog> dialogs_dict = new ConcurrentHashMap<>(100, 1.0f, 2);
    public HashMap<Long, MessageObject> dialogMessage = new HashMap<>();
    public HashMap<Long, MessageObject> dialogMessagesByRandomIds = new HashMap<>();
    public HashMap<Integer, MessageObject> dialogMessagesByIds = new HashMap<>();
    public ConcurrentHashMap<Long, ArrayList<PrintingUser>> printingUsers = new ConcurrentHashMap<>(20, 1.0f, 2);
    public HashMap<Long, CharSequence> printingStrings = new HashMap<>();
    public HashMap<Long, Integer> printingStringsTypes = new HashMap<>();
    public HashMap<Integer, HashMap<Long, Boolean>> sendingTypings = new HashMap<>();
    public ConcurrentHashMap<Integer, Integer> onlinePrivacy = new ConcurrentHashMap<>(20, 1.0f, 2);
    private int lastPrintingStringCount = 0;

    private HashMap<Long, Boolean> loadingPeerSettings = new HashMap<>();

    private ArrayList<Long> createdDialogIds = new ArrayList<>();

    private SparseIntArray shortPollChannels = new SparseIntArray();
    private SparseIntArray needShortPollChannels = new SparseIntArray();

    public boolean loadingBlockedUsers = false;
    public ArrayList<Integer> blockedUsers = new ArrayList<>();

    private SparseArray<ArrayList<Integer>> channelViewsToSend = new SparseArray<>();
    private SparseArray<ArrayList<Integer>> channelViewsToReload = new SparseArray<>();
    private long lastViewsCheckTime;

    private HashMap<Integer, ArrayList<TLRPC.Updates>> updatesQueueChannels = new HashMap<>();
    private HashMap<Integer, Long> updatesStartWaitTimeChannels = new HashMap<>();
    private HashMap<Integer, Integer> channelsPts = new HashMap<>();
    private HashMap<Integer, Boolean> gettingDifferenceChannels = new HashMap<>();

    private ArrayList<TLRPC.Updates> updatesQueueSeq = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueuePts = new ArrayList<>();
    private ArrayList<TLRPC.Updates> updatesQueueQts = new ArrayList<>();
    private long updatesStartWaitTimeSeq = 0;
    private long updatesStartWaitTimePts = 0;
    private long updatesStartWaitTimeQts = 0;
    private HashMap<Integer, String> fullUsersAbout = new HashMap<>();
    private ArrayList<Integer> loadingFullUsers = new ArrayList<>();
    private ArrayList<Integer> loadedFullUsers = new ArrayList<>();
    private ArrayList<Integer> loadingFullChats = new ArrayList<>();
    private ArrayList<Integer> loadingFullParticipants = new ArrayList<>();
    private ArrayList<Integer> loadedFullParticipants = new ArrayList<>();
    private ArrayList<Integer> loadedFullChats = new ArrayList<>();

    private HashMap<String, ArrayList<MessageObject>> reloadingWebpages = new HashMap<>();
    private HashMap<Long, ArrayList<MessageObject>> reloadingWebpagesPending = new HashMap<>();

    private HashMap<Long, ArrayList<Integer>> reloadingMessages = new HashMap<>();

    private boolean gettingNewDeleteTask = false;
    private int currentDeletingTaskTime = 0;
    private ArrayList<Integer> currentDeletingTaskMids = null;
    private Runnable currentDeleteTaskRunnable = null;

    public boolean loadingDialogs = false;
    private boolean migratingDialogs = false;
    public boolean dialogsEndReached = false;
    public boolean gettingDifference = false;
    public boolean updatingState = false;
    public boolean firstGettingTask = false;
    public boolean registeringForPush = false;

    public int secretWebpagePreview = 2;

    private long lastStatusUpdateTime = 0;
    private int statusRequest = 0;
    private int statusSettingState = 0;
    private boolean offlineSent = false;
    private String uploadingAvatar = null;

    public boolean enableJoined = true;
    public int fontSize = AndroidUtilities.dp(16);
    public int maxGroupCount = 200;
    public int maxBroadcastCount = 100;
    public int maxMegagroupCount = 5000;
    public int minGroupConvertSize = 200;
    public int maxEditTime = 172800;
    public int groupBigSize;
    private ArrayList<TLRPC.TL_disabledFeature> disabledFeatures = new ArrayList<>();

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
    public static final int UPDATE_MASK_ALL = UPDATE_MASK_AVATAR | UPDATE_MASK_STATUS | UPDATE_MASK_NAME | UPDATE_MASK_CHAT_AVATAR | UPDATE_MASK_CHAT_NAME | UPDATE_MASK_CHAT_MEMBERS | UPDATE_MASK_USER_PRINT | UPDATE_MASK_USER_PHONE | UPDATE_MASK_READ_DIALOG_MESSAGE | UPDATE_MASK_PHONE;

    public static class PrintingUser {
        public long lastTime;
        public int userId;
        public TLRPC.SendMessageAction action;
    }

    private static volatile MessagesController Instance = null;

    public static MessagesController getInstance() {
        MessagesController localInstance = Instance;
        if (localInstance == null) {
            synchronized (MessagesController.class) {
                localInstance = Instance;
                if (localInstance == null) {
                    Instance = localInstance = new MessagesController();
                }
            }
        }
        return localInstance;
    }

    public MessagesController() {
        ImageLoader.getInstance();
        MessagesStorage.getInstance();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidUpload);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailUpload);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.messageReceivedByServer);
        addSupportUser();
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        enableJoined = preferences.getBoolean("EnableContactJoined", true);

        preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
        secretWebpagePreview = preferences.getInt("secretWebpage2", 2);
        maxGroupCount = preferences.getInt("maxGroupCount", 200);
        maxMegagroupCount = preferences.getInt("maxMegagroupCount", 1000);
        maxEditTime = preferences.getInt("maxEditTime", 3600);
        groupBigSize = preferences.getInt("groupBigSize", 10);
        fontSize = preferences.getInt("fons_size", AndroidUtilities.isTablet() ? 18 : 16);
        String disabledFeaturesString = preferences.getString("disabledFeatures", null);
        if (disabledFeaturesString != null && disabledFeaturesString.length() != 0) {
            try {
                byte[] bytes = Base64.decode(disabledFeaturesString, Base64.DEFAULT);
                if (bytes != null) {
                    SerializedData data = new SerializedData(bytes);
                    int count = data.readInt32(false);
                    for (int a = 0; a < count; a++) {
                        TLRPC.TL_disabledFeature feature = TLRPC.TL_disabledFeature.TLdeserialize(data, data.readInt32(false), false);
                        if (feature != null && feature.feature != null && feature.description != null) {
                            disabledFeatures.add(feature);
                        }
                    }
                }
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    public void updateConfig(final TLRPC.TL_config config) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                //maxBroadcastCount = config.broadcast_size_max;
                maxMegagroupCount = config.megagroup_size_max;
                maxGroupCount = config.chat_size_max;
                groupBigSize = config.chat_big_size;
                disabledFeatures = config.disabled_features;
                maxEditTime = config.edit_time_limit;

                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("maxGroupCount", maxGroupCount);
                //editor.putInt("maxBroadcastCount", maxBroadcastCount);
                editor.putInt("maxMegagroupCount", maxMegagroupCount);
                editor.putInt("groupBigSize", groupBigSize);
                editor.putInt("maxEditTime", maxEditTime);
                try {
                    SerializedData data = new SerializedData();
                    data.writeInt32(disabledFeatures.size());
                    for (TLRPC.TL_disabledFeature disabledFeature : disabledFeatures) {
                        disabledFeature.serializeToStream(data);
                    }
                    String string = Base64.encodeToString(data.toByteArray(), Base64.DEFAULT);
                    if (string.length() != 0) {
                        editor.putString("disabledFeatures", string);
                    }
                } catch (Exception e) {
                    editor.remove("disabledFeatures");
                    FileLog.e("tmessages", e);
                }
                editor.commit();
            }
        });
    }

    public static boolean isFeatureEnabled(String feature, BaseFragment fragment) {
        if (feature == null || feature.length() == 0 || getInstance().disabledFeatures.isEmpty() || fragment == null) {
            return true;
        }
        for (TLRPC.TL_disabledFeature disabledFeature : getInstance().disabledFeatures) {
            if (disabledFeature.feature.equals(feature)) {
                if (fragment.getParentActivity() != null) {
                    AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
                    builder.setTitle("Oops!");
                    builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                    builder.setMessage(disabledFeature.description);
                    fragment.showDialog(builder.create());
                }
                return false;
            }
        }
        return true;
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

    public static TLRPC.InputUser getInputUser(TLRPC.User user) {
        if (user == null) {
            return new TLRPC.TL_inputUserEmpty();
        }
        TLRPC.InputUser inputUser;
        if (user.id == UserConfig.getClientUserId()) {
            inputUser = new TLRPC.TL_inputUserSelf();
        } else {
            inputUser = new TLRPC.TL_inputUser();
            inputUser.user_id = user.id;
            inputUser.access_hash = user.access_hash;
        }
        return inputUser;
    }

    public static TLRPC.InputUser getInputUser(int user_id) {
        TLRPC.User user = getInstance().getUser(user_id);
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

    public static TLRPC.InputChannel getInputChannel(int chatId) {
        return getInputChannel(getInstance().getChat(chatId));
    }

    public static TLRPC.InputPeer getInputPeer(int id) {
        TLRPC.InputPeer inputPeer;
        if (id < 0) {
            TLRPC.Chat chat = getInstance().getChat(-id);
            if (ChatObject.isChannel(chat)) {
                inputPeer = new TLRPC.TL_inputPeerChannel();
                inputPeer.channel_id = -id;
                inputPeer.access_hash = chat.access_hash;
            } else {
                inputPeer = new TLRPC.TL_inputPeerChat();
                inputPeer.chat_id = -id;
            }
        } else {
            TLRPC.User user = getInstance().getUser(id);
            inputPeer = new TLRPC.TL_inputPeerUser();
            inputPeer.user_id = id;
            if (user != null) {
                inputPeer.access_hash = user.access_hash;
            }
        }
        return inputPeer;
    }

    public static TLRPC.Peer getPeer(int id) {
        TLRPC.Peer inputPeer;
        if (id < 0) {
            TLRPC.Chat chat = getInstance().getChat(-id);
            if (chat instanceof TLRPC.TL_channel || chat instanceof TLRPC.TL_channelForbidden) {
                inputPeer = new TLRPC.TL_peerChannel();
                inputPeer.channel_id = -id;
            } else {
                inputPeer = new TLRPC.TL_peerChat();
                inputPeer.chat_id = -id;
            }
        } else {
            TLRPC.User user = getInstance().getUser(id);
            inputPeer = new TLRPC.TL_peerUser();
            inputPeer.user_id = id;
        }
        return inputPeer;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            final String location = (String) args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile) args[1];

            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                req.caption = "";
                req.crop = new TLRPC.TL_inputPhotoCropAuto();
                req.file = file;
                req.geo_point = new TLRPC.TL_inputGeoPointEmpty();
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            TLRPC.User user = getUser(UserConfig.getClientUserId());
                            if (user == null) {
                                user = UserConfig.getCurrentUser();
                                putUser(user, true);
                            } else {
                                UserConfig.setCurrentUser(user);
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
                            MessagesStorage.getInstance().clearUserPhotos(user.id);
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(user);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_AVATAR);
                                    UserConfig.saveConfig(true);
                                }
                            });
                        }
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
            if (obj != null && obj.getId() == msgId) {
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;
                TLRPC.Dialog dialog = dialogs_dict.get(did);
                if (dialog != null) {
                    if (dialog.top_message == msgId) {
                        dialog.top_message = newMsgId;
                    }
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
            obj = dialogMessagesByIds.remove(msgId);
            if (obj != null) {
                dialogMessagesByIds.put(newMsgId, obj);
            }
        }
    }

    public void cleanUp() {
        ContactsController.getInstance().cleanup();
        MediaController.getInstance().cleanup();
        NotificationsController.getInstance().cleanup();
        SendMessagesHelper.getInstance().cleanUp();
        SecretChatHelper.getInstance().cleanUp();
        StickersQuery.cleanup();

        reloadingWebpages.clear();
        reloadingWebpagesPending.clear();
        dialogs_dict.clear();
        dialogs_read_inbox_max.clear();
        exportedChats.clear();
        fullUsersAbout.clear();
        dialogs.clear();
        joiningToChannels.clear();
        channelViewsToSend.clear();
        channelViewsToReload.clear();
        dialogsServerOnly.clear();
        dialogsGroupsOnly.clear();
        dialogMessagesByIds.clear();
        dialogMessagesByRandomIds.clear();
        users.clear();
        usersByUsernames.clear();
        chats.clear();
        dialogMessage.clear();
        printingUsers.clear();
        printingStrings.clear();
        printingStringsTypes.clear();
        onlinePrivacy.clear();
        loadingPeerSettings.clear();
        lastPrintingStringCount = 0;
        nextDialogsCacheOffset = 0;
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                updatesQueueSeq.clear();
                updatesQueuePts.clear();
                updatesQueueQts.clear();
                updatesStartWaitTimeSeq = 0;
                updatesStartWaitTimePts = 0;
                updatesStartWaitTimeQts = 0;
                createdDialogIds.clear();
                gettingDifference = false;
            }
        });
        blockedUsers.clear();
        sendingTypings.clear();
        loadingFullUsers.clear();
        loadedFullUsers.clear();
        reloadingMessages.clear();
        loadingFullChats.clear();
        loadingFullParticipants.clear();
        loadedFullParticipants.clear();
        loadedFullChats.clear();

        currentDeletingTaskTime = 0;
        currentDeletingTaskMids = null;
        gettingNewDeleteTask = false;
        loadingDialogs = false;
        dialogsEndReached = false;
        loadingBlockedUsers = false;
        firstGettingTask = false;
        updatingState = false;
        lastStatusUpdateTime = 0;
        offlineSent = false;
        registeringForPush = false;
        uploadingAvatar = null;
        statusRequest = 0;
        statusSettingState = 0;

        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                ConnectionsManager.getInstance().setIsUpdating(false);
                updatesQueueChannels.clear();
                updatesStartWaitTimeChannels.clear();
                gettingDifferenceChannels.clear();
                channelsPts.clear();
                shortPollChannels.clear();
                needShortPollChannels.clear();
            }
        });

        if (currentDeleteTaskRunnable != null) {
            Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            currentDeleteTaskRunnable = null;
        }

        addSupportUser();
    }

    public TLRPC.User getUser(Integer id) {
        return users.get(id);
    }

    public TLRPC.User getUser(String username) {
        if (username == null || username.length() == 0) {
            return null;
        }
        return usersByUsernames.get(username.toLowerCase());
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

    public TLRPC.EncryptedChat getEncryptedChatDB(int chat_id) {
        TLRPC.EncryptedChat chat = encryptedChats.get(chat_id);
        if (chat == null) {
            Semaphore semaphore = new Semaphore(0);
            ArrayList<TLObject> result = new ArrayList<>();
            MessagesStorage.getInstance().getEncryptedChat(chat_id, semaphore, result);
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
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

    public void setLastCreatedDialogId(final long dialog_id, final boolean set) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (set) {
                    createdDialogIds.add(dialog_id);
                } else {
                    createdDialogIds.remove(dialog_id);
                }
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
        if (oldUser != null && oldUser.username != null && oldUser.username.length() > 0) {
            usersByUsernames.remove(oldUser.username);
        }
        if (user.username != null && user.username.length() > 0) {
            usersByUsernames.put(user.username.toLowerCase(), user);
        }
        if (user.min) {
            if (oldUser != null) {
                if (!fromCache) {
                    if (user.username != null) {
                        oldUser.username = user.username;
                        oldUser.flags |= 8;
                    } else {
                        oldUser.username = null;
                        oldUser.flags = oldUser.flags &~ 8;
                    }
                    if (user.photo != null) {
                        oldUser.photo = user.photo;
                        oldUser.flags |= 32;
                    } else {
                        oldUser.photo = null;
                        oldUser.flags = oldUser.flags &~ 32;
                    }
                }
            } else {
                users.put(user.id, user);
            }
        } else {
            if (!fromCache) {
                users.put(user.id, user);
                if (user.id == UserConfig.getClientUserId()) {
                    UserConfig.setCurrentUser(user);
                    UserConfig.saveConfig(true);
                }
                if (oldUser != null && user.status != null && oldUser.status != null && user.status.expires != oldUser.status.expires) {
                    return true;
                }
            } else if (oldUser == null) {
                users.put(user.id, user);
            } else if (oldUser.min) {
                user.min = false;
                if (oldUser.username != null) {
                    user.username = oldUser.username;
                    user.flags |= 8;
                } else {
                    user.username = null;
                    user.flags = user.flags &~ 8;
                }
                if (oldUser.photo != null) {
                    user.photo = oldUser.photo;
                    user.flags |= 32;
                } else {
                    user.photo = null;
                    user.flags = user.flags &~ 32;
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
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS);
                }
            });
        }
    }

    public void putChat(TLRPC.Chat chat, boolean fromCache) {
        if (chat == null) {
            return;
        }
        TLRPC.Chat oldChat = chats.get(chat.id);

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
                        oldChat.username = null;
                        oldChat.flags = oldChat.flags &~ 64;
                    }
                }
            } else {
                chats.put(chat.id, chat);
            }
        } else {
            if (!fromCache) {
                if (oldChat != null && chat.version != oldChat.version) {
                    loadedFullChats.remove((Integer) chat.id);
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
                    chat.username = null;
                    chat.flags = chat.flags &~ 64;
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

    public String getUserAbout(int uid) {
        return fullUsersAbout.get(uid);
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

    public void loadFullChat(final int chat_id, final int classGuid, boolean force) {
        if (loadingFullChats.contains(chat_id) || !force && loadedFullChats.contains(chat_id)) {
            return;
        }
        loadingFullChats.add(chat_id);
        TLObject request;
        final TLRPC.Chat chat = getChat(chat_id);
        if (ChatObject.isChannel(chat_id)) {
            TLRPC.TL_channels_getFullChannel req = new TLRPC.TL_channels_getFullChannel();
            req.channel = getInputChannel(chat_id);
            request = req;
        } else {
            TLRPC.TL_messages_getFullChat req = new TLRPC.TL_messages_getFullChat();
            req.chat_id = chat_id;
            request = req;
        }
        int reqId = ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
            @Override
            public void run(TLObject response, final TLRPC.TL_error error) {
                if (error == null) {
                    final TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull) response;
                    if (chat != null && chat.megagroup) {
                        res.full_chat.unread_important_count = Math.max(res.full_chat.unread_important_count, res.full_chat.unread_count);
                    }
                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                    MessagesStorage.getInstance().updateChatInfo(res.full_chat, false);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            applyDialogNotificationsSettings(-chat_id, res.full_chat.notify_settings);
                            for (int a = 0; a < res.full_chat.bot_info.size(); a++) {
                                TLRPC.BotInfo botInfo = res.full_chat.bot_info.get(a);
                                BotQuery.putBotInfo(botInfo);
                            }
                            exportedChats.put(chat_id, res.full_chat.exported_invite);
                            loadingFullChats.remove((Integer) chat_id);
                            loadedFullChats.add(chat_id);
                            if (!res.chats.isEmpty()) {
                                TLRPC.Chat chat = res.chats.get(0);
                                chat.address = res.full_chat.about;
                            }

                            putUsers(res.users, false);
                            putChats(res.chats, false);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, res.full_chat, classGuid, false, null);
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            checkChannelError(error.text, chat_id);
                            loadingFullChats.remove((Integer) chat_id);
                        }
                    });
                }
            }
        });
        if (classGuid != 0) {
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void loadFullUser(final TLRPC.User user, final int classGuid, boolean force) {
        if (user == null || loadingFullUsers.contains(user.id) || !force && loadedFullUsers.contains(user.id)) {
            return;
        }
        loadingFullUsers.add(user.id);
        TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
        req.id = getInputUser(user);
        int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.TL_userFull userFull = (TLRPC.TL_userFull) response;
                            applyDialogNotificationsSettings(user.id, userFull.notify_settings);
                            if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                                BotQuery.putBotInfo(userFull.bot_info);
                            }
                            if (userFull.about != null && userFull.about.length() > 0) {
                                fullUsersAbout.put(user.id, userFull.about);
                            } else {
                                fullUsersAbout.remove(user.id);
                            }
                            loadingFullUsers.remove((Integer) user.id);
                            loadedFullUsers.add(user.id);
                            String names = user.first_name + user.last_name + user.username;
                            ArrayList<TLRPC.User> users = new ArrayList<>();
                            users.add(userFull.user);
                            putUsers(users, false);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                            if (names != null && !names.equals(userFull.user.first_name + userFull.user.last_name + userFull.user.username)) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_NAME);
                            }
                            if (userFull.bot_info instanceof TLRPC.TL_botInfo) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.botInfoDidLoaded, userFull.bot_info, classGuid);
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.userInfoDidLoaded, user.id);
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingFullUsers.remove((Integer) user.id);
                        }
                    });
                }
            }
        });
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
    }

    private void reloadMessages(final ArrayList<Integer> mids, final long dialog_id) {
        if (mids.isEmpty()) {
            return;
        }
        TLObject request;
        final ArrayList<Integer> result = new ArrayList<>();
        final TLRPC.Chat chat = ChatObject.getChatByDialog(dialog_id);
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
        ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;

                    final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<>();
                    for (int a = 0; a < messagesRes.users.size(); a++) {
                        TLRPC.User u = messagesRes.users.get(a);
                        usersLocal.put(u.id, u);
                    }
                    final HashMap<Integer, TLRPC.Chat> chatsLocal = new HashMap<>();
                    for (int a = 0; a < messagesRes.chats.size(); a++) {
                        TLRPC.Chat c = messagesRes.chats.get(a);
                        chatsLocal.put(c.id, c);
                    }

                    final ArrayList<MessageObject> objects = new ArrayList<>();
                    for (int a = 0; a < messagesRes.messages.size(); a++) {
                        TLRPC.Message message = messagesRes.messages.get(a);
                        if (chat != null && chat.megagroup) {
                            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                        }
                        message.dialog_id = dialog_id;
                        objects.add(new MessageObject(message, usersLocal, chatsLocal, true));
                    }

                    ImageLoader.saveMessagesThumbs(messagesRes.messages);
                    MessagesStorage.getInstance().putMessages(messagesRes, dialog_id, -1, 0, 0, false);

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<Integer> arrayList = reloadingMessages.get(dialog_id);
                            if (arrayList != null) {
                                arrayList.removeAll(result);
                                if (arrayList.isEmpty()) {
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
                                            obj = dialogMessagesByIds.remove(obj.getId());
                                            if (obj != null) {
                                                dialogMessagesByIds.put(obj.getId(), obj);
                                            }
                                        }
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                        break;
                                    }
                                }
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, objects);
                        }
                    });
                }
            }
        });
    }

    public void hideReportSpam(final long dialogId, TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (currentUser == null && currentChat == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("spam3_" + dialogId, 1);
        editor.commit();
        TLRPC.TL_messages_hideReportSpam req = new TLRPC.TL_messages_hideReportSpam();
        if (currentUser != null) {
            req.peer = MessagesController.getInputPeer(currentUser.id);
        } else if (currentChat != null) {
            req.peer = MessagesController.getInputPeer(-currentChat.id);
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void reportSpam(final long dialogId, TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (currentUser == null && currentChat == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        SharedPreferences.Editor editor = preferences.edit();
        editor.putInt("spam3_" + dialogId, 1);
        editor.commit();
        TLRPC.TL_messages_reportSpam req = new TLRPC.TL_messages_reportSpam();
        if (currentChat != null) {
            req.peer = MessagesController.getInputPeer(-currentChat.id);
        } else if (currentUser != null) {
            req.peer = MessagesController.getInputPeer(currentUser.id);
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public void loadPeerSettings(final long dialogId, TLRPC.User currentUser, TLRPC.Chat currentChat) {
        if (loadingPeerSettings.containsKey(dialogId) || currentUser == null && currentChat == null) {
            return;
        }
        loadingPeerSettings.put(dialogId, true);
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        if (preferences.getInt("spam3_" + dialogId, 0) == 1) {
            return;
        }
        boolean hidden = preferences.getBoolean("spam_" + dialogId, false);
        if (hidden) {
            TLRPC.TL_messages_hideReportSpam req = new TLRPC.TL_messages_hideReportSpam();
            if (currentUser != null) {
                req.peer = MessagesController.getInputPeer(currentUser.id);
            } else if (currentChat != null) {
                req.peer = MessagesController.getInputPeer(-currentChat.id);
            }
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingPeerSettings.remove(dialogId);
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            editor.remove("spam_" + dialogId);
                            editor.putInt("spam3_" + dialogId, 1);
                            editor.commit();
                        }
                    });
                }
            });
            return;
        }
        TLRPC.TL_messages_getPeerSettings req = new TLRPC.TL_messages_getPeerSettings();
        if (currentUser != null) {
            req.peer = MessagesController.getInputPeer(currentUser.id);
        } else if (currentChat != null) {
            req.peer = MessagesController.getInputPeer(-currentChat.id);
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        loadingPeerSettings.remove(dialogId);
                        if (response != null) {
                            TLRPC.TL_peerSettings res = (TLRPC.TL_peerSettings) response;
                            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                            SharedPreferences.Editor editor = preferences.edit();
                            if (!res.report_spam) {
                                editor.putInt("spam3_" + dialogId, 1);
                            } else {
                                editor.putInt("spam3_" + dialogId, 2);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.peerSettingsDidLoaded, dialogId);
                            }
                            editor.commit();
                        }
                    }
                });
            }
        });
    }

    protected void processNewChannelDifferenceParams(int pts, int pts_count, int channelId) {
        FileLog.e("tmessages", "processNewChannelDifferenceParams pts = " + pts + " pts_count = " + pts_count + " channeldId = " + channelId);
        TLRPC.Dialog dialog = dialogs_dict.get((long) -channelId);
        if (dialog instanceof TLRPC.TL_dialogChannel) {
            Integer channelPts = channelsPts.get(channelId);
            if (channelPts == null) {
                channelPts = MessagesStorage.getInstance().getChannelPtsSync(channelId);
                if (channelPts == 0) {
                    channelPts = 1;
                }
                channelsPts.put(channelId, channelPts);
            }
            if (channelPts + pts_count == pts) {
                FileLog.e("tmessages", "APPLY CHANNEL PTS");
                channelsPts.put(channelId, pts);
                MessagesStorage.getInstance().saveChannelPts(channelId, pts);
            } else if (channelPts != pts) {
                Long updatesStartWaitTime = updatesStartWaitTimeChannels.get(channelId);
                Boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId);
                if (gettingDifferenceChannel == null) {
                    gettingDifferenceChannel = false;
                }
                if (gettingDifferenceChannel || updatesStartWaitTime == null || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
                    FileLog.e("tmessages", "ADD CHANNEL UPDATE TO QUEUE pts = " + pts + " pts_count = " + pts_count);
                    if (updatesStartWaitTime == null) {
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
    }

    protected void processNewDifferenceParams(int seq, int pts, int date, int pts_count) {
        FileLog.e("tmessages", "processNewDifferenceParams seq = " + seq + " pts = " + pts + " date = " + date + " pts_count = " + pts_count);
        if (pts != -1) {
            if (MessagesStorage.lastPtsValue + pts_count == pts) {
                FileLog.e("tmessages", "APPLY PTS");
                MessagesStorage.lastPtsValue = pts;
                MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
            } else if (MessagesStorage.lastPtsValue != pts) {
                if (gettingDifference || updatesStartWaitTimePts == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
                    FileLog.e("tmessages", "ADD UPDATE TO QUEUE pts = " + pts + " pts_count = " + pts_count);
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
            if (MessagesStorage.lastSeqValue + 1 == seq) {
                FileLog.e("tmessages", "APPLY SEQ");
                MessagesStorage.lastSeqValue = seq;
                if (date != -1) {
                    MessagesStorage.lastDateValue = date;
                }
                MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
            } else if (MessagesStorage.lastSeqValue != seq) {
                if (gettingDifference || updatesStartWaitTimeSeq == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimeSeq) <= 1500) {
                    FileLog.e("tmessages", "ADD UPDATE TO QUEUE seq = " + seq);
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

    public void didAddedNewTask(final int minDate, final SparseArray<ArrayList<Integer>> mids) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (currentDeletingTaskMids == null && !gettingNewDeleteTask || currentDeletingTaskTime != 0 && minDate < currentDeletingTaskTime) {
                    getNewDeleteTask(null);
                }
            }
        });
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.didCreatedNewDeleteTask, mids);
            }
        });
    }

    public void getNewDeleteTask(final ArrayList<Integer> oldTask) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                gettingNewDeleteTask = true;
                MessagesStorage.getInstance().getNewTask(oldTask);
            }
        });
    }

    private boolean checkDeletingTask(boolean runnable) {
        int currentServerTime = ConnectionsManager.getInstance().getCurrentTime();

        if (currentDeletingTaskMids != null && (runnable || currentDeletingTaskTime != 0 && currentDeletingTaskTime <= currentServerTime)) {
            currentDeletingTaskTime = 0;
            if (currentDeleteTaskRunnable != null && !runnable) {
                Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            }
            currentDeleteTaskRunnable = null;
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    deleteMessages(currentDeletingTaskMids, null, null, 0);

                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            getNewDeleteTask(currentDeletingTaskMids);
                            currentDeletingTaskTime = 0;
                            currentDeletingTaskMids = null;
                        }
                    });
                }
            });
            return true;
        }
        return false;
    }

    public void processLoadedDeleteTask(final int taskTime, final ArrayList<Integer> messages) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                gettingNewDeleteTask = false;
                if (messages != null) {
                    currentDeletingTaskTime = taskTime;
                    currentDeletingTaskMids = messages;

                    if (currentDeleteTaskRunnable != null) {
                        Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
                        currentDeleteTaskRunnable = null;
                    }

                    if (!checkDeletingTask(false)) {
                        currentDeleteTaskRunnable = new Runnable() {
                            @Override
                            public void run() {
                                checkDeletingTask(true);
                            }
                        };
                        int currentServerTime = ConnectionsManager.getInstance().getCurrentTime();
                        Utilities.stageQueue.postRunnable(currentDeleteTaskRunnable, (long) Math.abs(currentServerTime - currentDeletingTaskTime) * 1000);
                    }
                } else {
                    currentDeletingTaskTime = 0;
                    currentDeletingTaskMids = null;
                }
            }
        });
    }

    public void loadUserPhotos(final int uid, final int offset, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (fromCache) {
            MessagesStorage.getInstance().getUserPhotos(uid, offset, count, max_id, classGuid);
        } else {
            TLRPC.User user = getUser(uid);
            if (user == null) {
                return;
            }
            TLRPC.TL_photos_getUserPhotos req = new TLRPC.TL_photos_getUserPhotos();
            req.limit = count;
            req.offset = offset;
            req.max_id = (int) max_id;
            req.user_id = getInputUser(user);
            int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.photos_Photos res = (TLRPC.photos_Photos) response;
                        processLoadedUserPhotos(res, uid, offset, count, max_id, false, classGuid);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void blockUser(int user_id) {
        final TLRPC.User user = getUser(user_id);
        if (user == null || blockedUsers.contains(user_id)) {
            return;
        }
        blockedUsers.add(user_id);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.blockedUsersDidLoaded);
        TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
        req.id = getInputUser(user);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    ArrayList<Integer> ids = new ArrayList<>();
                    ids.add(user.id);
                    MessagesStorage.getInstance().putBlockedUsers(ids, false);
                }
            }
        });
    }

    public void unblockUser(int user_id) {
        TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
        final TLRPC.User user = getUser(user_id);
        if (user == null) {
            return;
        }
        blockedUsers.remove((Integer) user.id);
        req.id = getInputUser(user);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.blockedUsersDidLoaded);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                MessagesStorage.getInstance().deleteBlockedUser(user.id);
            }
        });
    }

    public void getBlockedUsers(boolean cache) {
        if (!UserConfig.isClientActivated() || loadingBlockedUsers) {
            return;
        }
        loadingBlockedUsers = true;
        if (cache) {
            MessagesStorage.getInstance().getBlockedUsers();
        } else {
            TLRPC.TL_contacts_getBlocked req = new TLRPC.TL_contacts_getBlocked();
            req.offset = 0;
            req.limit = 200;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    ArrayList<Integer> blocked = new ArrayList<>();
                    ArrayList<TLRPC.User> users = null;
                    if (error == null) {
                        final TLRPC.contacts_Blocked res = (TLRPC.contacts_Blocked) response;
                        for (TLRPC.TL_contactBlocked contactBlocked : res.blocked) {
                            blocked.add(contactBlocked.user_id);
                        }
                        users = res.users;
                        MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);
                        MessagesStorage.getInstance().putBlockedUsers(blocked, true);
                    }
                    processLoadedBlockedUsers(blocked, users, false);
                }
            });
        }
    }

    public void processLoadedBlockedUsers(final ArrayList<Integer> ids, final ArrayList<TLRPC.User> users, final boolean cache) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (users != null) {
                    putUsers(users, cache);
                }
                loadingBlockedUsers = false;
                if (ids.isEmpty() && cache && !UserConfig.blockedUsersLoaded) {
                    getBlockedUsers(false);
                    return;
                } else if (!cache) {
                    UserConfig.blockedUsersLoaded = true;
                    UserConfig.saveConfig(false);
                }
                blockedUsers = ids;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.blockedUsersDidLoaded);
            }
        });
    }

    public void deleteUserPhoto(TLRPC.InputPhoto photo) {
        if (photo == null) {
            TLRPC.TL_photos_updateProfilePhoto req = new TLRPC.TL_photos_updateProfilePhoto();
            req.id = new TLRPC.TL_inputPhotoEmpty();
            req.crop = new TLRPC.TL_inputPhotoCropAuto();
            UserConfig.getCurrentUser().photo = new TLRPC.TL_userProfilePhotoEmpty();
            TLRPC.User user = getUser(UserConfig.getClientUserId());
            if (user == null) {
                user = UserConfig.getCurrentUser();
            }
            if (user == null) {
                return;
            }
            user.photo = UserConfig.getCurrentUser().photo;
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL);
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.User user = getUser(UserConfig.getClientUserId());
                        if (user == null) {
                            user = UserConfig.getCurrentUser();
                            putUser(user, false);
                        } else {
                            UserConfig.setCurrentUser(user);
                        }
                        if (user == null) {
                            return;
                        }
                        MessagesStorage.getInstance().clearUserPhotos(user.id);
                        ArrayList<TLRPC.User> users = new ArrayList<>();
                        users.add(user);
                        MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                        user.photo = (TLRPC.UserProfilePhoto) response;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_ALL);
                                UserConfig.saveConfig(true);
                            }
                        });
                    }
                }
            });
        } else {
            TLRPC.TL_photos_deletePhotos req = new TLRPC.TL_photos_deletePhotos();
            req.id.add(photo);
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
    }

    public void processLoadedUserPhotos(final TLRPC.photos_Photos res, final int uid, final int offset, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (!fromCache) {
            MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);
            MessagesStorage.getInstance().putUserPhotos(uid, res);
        } else if (res == null || res.photos.isEmpty()) {
            loadUserPhotos(uid, offset, count, max_id, false, classGuid);
            return;
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                putUsers(res.users, fromCache);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.userPhotosLoaded, uid, offset, count, fromCache, classGuid, res.photos);
            }
        });
    }

    public void uploadAndApplyUserAvatar(TLRPC.PhotoSize bigPhoto) {
        if (bigPhoto != null) {
            uploadingAvatar = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
            FileLoader.getInstance().uploadFile(uploadingAvatar, false, true);
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

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat, final int channelId) {
        if (messages == null || messages.isEmpty()) {
            return;
        }
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
        ArrayList<Integer> toSend = new ArrayList<>();
        for (int a = 0; a < messages.size(); a++) {
            Integer mid = messages.get(a);
            if (mid > 0) {
                toSend.add(mid);
            }
        }
        MessagesStorage.getInstance().markMessagesAsDeleted(messages, true, channelId);
        MessagesStorage.getInstance().updateDialogsWithDeletedMessages(messages, true, channelId);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesDeleted, messages, channelId);
        if (channelId != 0) {
            TLRPC.TL_channels_deleteMessages req = new TLRPC.TL_channels_deleteMessages();
            req.id = toSend;
            req.channel = getInputChannel(channelId);
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                        processNewChannelDifferenceParams(res.pts, res.pts_count, channelId);
                    }
                }
            });
        } else {
            if (randoms != null && encryptedChat != null && !randoms.isEmpty()) {
                SecretChatHelper.getInstance().sendMessagesDeleteMessage(encryptedChat, randoms, null);
            }
            TLRPC.TL_messages_deleteMessages req = new TLRPC.TL_messages_deleteMessages();
            req.id = toSend;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    }
                }
            });
        }
    }

    public void pinChannelMessage(TLRPC.Chat chat, int id, boolean notify) {
        TLRPC.TL_channels_updatePinnedMessage req = new TLRPC.TL_channels_updatePinnedMessage();
        req.channel = getInputChannel(chat);
        req.id = id;
        req.silent = !notify;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    processUpdates(updates, false);
                }
            }
        });
    }

    public void deleteDialog(final long did, final int onlyHistory) {
        deleteDialog(did, true, onlyHistory, 0);
    }

    public void deleteUserChannelHistory(final TLRPC.Chat chat, final TLRPC.User user, int offset) {
        if (offset == 0) {
            MessagesStorage.getInstance().deleteUserChannelHistory(chat.id, user.id);
        }
        TLRPC.TL_channels_deleteUserHistory req = new TLRPC.TL_channels_deleteUserHistory();
        req.channel = getInputChannel(chat);
        req.user_id = getInputUser(user);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                    if (res.offset > 0) {
                        deleteUserChannelHistory(chat, user, res.offset);
                    }
                    processNewChannelDifferenceParams(res.pts, res.pts_count, chat.id);
                }
            }
        });
    }

    private void deleteDialog(final long did, final boolean first, final int onlyHistory, final int max_id) {
        int lower_part = (int) did;
        int high_id = (int) (did >> 32);
        int max_id_delete = max_id;

        if (onlyHistory == 2) {
            MessagesStorage.getInstance().deleteDialog(did, onlyHistory);
            return;
        }

        if (first) {
            TLRPC.Dialog dialog = dialogs_dict.get(did);
            if (dialog != null) {
                if (max_id_delete == 0) {
                    max_id_delete = Math.max(0, dialog.top_message);
                }
                if (onlyHistory == 0) {
                    dialogs.remove(dialog);
                    if (dialogsServerOnly.remove(dialog)) {
                        if (dialog instanceof TLRPC.TL_dialogChannel) {
                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    channelsPts.remove(-(int) did);
                                    shortPollChannels.delete(-(int) did);
                                    needShortPollChannels.delete(-(int) did);
                                }
                            });
                        }
                    }
                    dialogsGroupsOnly.remove(dialog);
                    dialogs_dict.remove(did);
                    dialogs_read_inbox_max.remove(did);
                    nextDialogsCacheOffset--;
                } else {
                    dialog.unread_count = 0;
                }
                dialogMessage.remove(dialog.id);
                MessageObject object = dialogMessagesByIds.remove(dialog.top_message);
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = 0;
            }
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did, false);
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationsController.getInstance().removeNotificationsForDialog(did);
                        }
                    });
                }
            });

            MessagesStorage.getInstance().deleteDialog(did, onlyHistory);
        }

        if (high_id == 1) {
            return;
        }

        if (lower_part != 0) {
            TLRPC.InputPeer peer = getInputPeer(lower_part);
            if (peer == null || peer instanceof TLRPC.TL_inputPeerChannel) {
                return;
            }
            TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
            req.peer = peer;
            req.max_id = max_id_delete;
            final int max_id_delete_final = max_id_delete;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                        if (res.offset > 0) {
                            deleteDialog(did, false, onlyHistory, max_id_delete_final);
                        }
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    }
                }
            }, ConnectionsManager.RequestFlagInvokeAfter);
        } else {
            if (onlyHistory == 1) {
                SecretChatHelper.getInstance().sendClearHistoryMessage(getEncryptedChat(high_id), null);
            } else {
                SecretChatHelper.getInstance().declineSecretChat(high_id);
            }
        }
    }

    public MediaController.SearchImage saveGif(TLRPC.Document document) {
        MediaController.SearchImage searchImage = new MediaController.SearchImage();
        searchImage.type = 2;
        searchImage.document = document;
        searchImage.date = (int) (System.currentTimeMillis() / 1000);
        searchImage.id = "" + searchImage.document.id;

        ArrayList<MediaController.SearchImage> arrayList = new ArrayList<>();
        arrayList.add(searchImage);
        MessagesStorage.getInstance().putWebRecent(arrayList);
        TLRPC.TL_messages_saveGif req = new TLRPC.TL_messages_saveGif();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = searchImage.document.id;
        req.id.access_hash = searchImage.document.access_hash;
        req.unsave = false;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
        return searchImage;
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
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (error == null) {
                            TLRPC.TL_channels_channelParticipants res = (TLRPC.TL_channels_channelParticipants) response;
                            putUsers(res.users, false);
                            MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);
                            MessagesStorage.getInstance().updateChannelUsers(chat_id, res.participants);
                            loadedFullParticipants.add(chat_id);
                        }
                        loadingFullParticipants.remove(chat_id);
                    }
                });
            }
        });
    }

    public void loadChatInfo(final int chat_id, Semaphore semaphore, boolean force) {
        MessagesStorage.getInstance().loadChatInfo(chat_id, semaphore, force, false);
    }

    public void processChatInfo(int chat_id, final TLRPC.ChatFull info, final ArrayList<TLRPC.User> usersArr, final boolean fromCache, boolean force, final boolean byChannelUsers, final MessageObject pinnedMessageObject) {
        if (fromCache && chat_id > 0 && !byChannelUsers) {
            loadFullChat(chat_id, 0, force);
        }
        if (info != null) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    putUsers(usersArr, fromCache);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, byChannelUsers, pinnedMessageObject);
                }
            });
        }
    }

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();

        checkDeletingTask(false);

        if (UserConfig.isClientActivated()) {
            if (ConnectionsManager.getInstance().getPauseTime() == 0 && ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePaused) {
                if (statusSettingState != 1 && (lastStatusUpdateTime == 0 || Math.abs(System.currentTimeMillis() - lastStatusUpdateTime) >= 55000 || offlineSent)) {
                    statusSettingState = 1;

                    if (statusRequest != 0) {
                        ConnectionsManager.getInstance().cancelRequest(statusRequest, true);
                    }

                    TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                    req.offline = false;
                    statusRequest = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
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
                        }
                    });
                }
            } else if (statusSettingState != 2 && !offlineSent && Math.abs(System.currentTimeMillis() - ConnectionsManager.getInstance().getPauseTime()) >= 2000) {
                statusSettingState = 2;
                if (statusRequest != 0) {
                    ConnectionsManager.getInstance().cancelRequest(statusRequest, true);
                }
                TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                req.offline = true;
                statusRequest = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            offlineSent = true;
                        } else {
                            if (lastStatusUpdateTime != 0) {
                                lastStatusUpdateTime += 5000;
                            }
                        }
                        statusRequest = 0;
                    }
                });
            }

            if (!updatesQueueChannels.isEmpty()) {
                ArrayList<Integer> keys = new ArrayList<>(updatesQueueChannels.keySet());
                for (int a = 0; a < keys.size(); a++) {
                    int key = keys.get(a);
                    Long updatesStartWaitTime = updatesStartWaitTimeChannels.get(key);
                    if (updatesStartWaitTime != null && updatesStartWaitTime + 1500 < currentTime) {
                        FileLog.e("tmessages", "QUEUE CHANNEL " + key + " UPDATES WAIT TIMEOUT - CHECK QUEUE");
                        processChannelsUpdatesQueue(key, 0);
                    }
                }
            }

            for (int a = 0; a < 3; a++) {
                if (getUpdatesStartTime(a) != 0 && getUpdatesStartTime(a) + 1500 < currentTime) {
                    FileLog.e("tmessages", a + " QUEUE UPDATES WAIT TIMEOUT - CHECK QUEUE");
                    processUpdatesQueue(a, 0);
                }
            }
        }
        if ((channelViewsToSend.size() != 0 || channelViewsToReload.size() != 0) && Math.abs(System.currentTimeMillis() - lastViewsCheckTime) >= 5000) {
            lastViewsCheckTime = System.currentTimeMillis();
            for (int b = 0; b < 2; b++) {
                SparseArray<ArrayList<Integer>> array = b == 0 ? channelViewsToSend : channelViewsToReload;
                if (array.size() == 0) {
                    continue;
                }
                for (int a = 0; a < array.size(); a++) {
                    final int key = array.keyAt(a);
                    final TLRPC.TL_messages_getMessagesViews req = new TLRPC.TL_messages_getMessagesViews();
                    req.peer = getInputPeer(key);
                    req.id = array.get(key);
                    req.increment = a == 0;
                    ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            if (error == null) {
                                TLRPC.Vector vector = (TLRPC.Vector) response;
                                final SparseArray<SparseIntArray> channelViews = new SparseArray<>();
                                SparseIntArray array = channelViews.get(key);
                                if (array == null) {
                                    array = new SparseIntArray();
                                    channelViews.put(key, array);
                                }
                                for (int a = 0; a < req.id.size(); a++) {
                                    if (a >= vector.objects.size()) {
                                        break;
                                    }
                                    array.put(req.id.get(a), (Integer) vector.objects.get(a));
                                }
                                MessagesStorage.getInstance().putChannelViews(channelViews, req.peer instanceof TLRPC.TL_inputPeerChannel);
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedMessagesViews, channelViews);
                                    }
                                });
                            }
                        }
                    });
                }
                array.clear();
            }
        }
        if (!onlinePrivacy.isEmpty()) {
            ArrayList<Integer> toRemove = null;
            int currentServerTime = ConnectionsManager.getInstance().getCurrentTime();
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
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS);
                    }
                });
            }
        }
        if (shortPollChannels.size() != 0) {
            for (int a = 0; a < shortPollChannels.size(); a++) {
                int key = shortPollChannels.keyAt(a);
                int timeout = shortPollChannels.get(key);
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
                Long key = keys.get(b);
                ArrayList<PrintingUser> arr = printingUsers.get(key);
                for (int a = 0; a < arr.size(); a++) {
                    PrintingUser user = arr.get(a);
                    if (user.lastTime + 5900 < currentTime) {
                        updated = true;
                        arr.remove(user);
                        a--;
                    }
                }
                if (arr.isEmpty()) {
                    printingUsers.remove(key);
                    keys.remove(b);
                    b--;
                }
            }

            updatePrintingStrings();

            if (updated) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                    }
                });
            }
        }
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
        final HashMap<Long, CharSequence> newPrintingStrings = new HashMap<>();
        final HashMap<Long, Integer> newPrintingStringsTypes = new HashMap<>();

        ArrayList<Long> keys = new ArrayList<>(printingUsers.keySet());
        for (HashMap.Entry<Long, ArrayList<PrintingUser>> entry : printingUsers.entrySet()) {
            long key = entry.getKey();
            ArrayList<PrintingUser> arr = entry.getValue();

            int lower_id = (int) key;

            if (lower_id > 0 || lower_id == 0 || arr.size() == 1) {
                PrintingUser pu = arr.get(0);
                TLRPC.User user = getUser(pu.userId);
                if (user == null) {
                    return;
                }
                if (pu.action instanceof TLRPC.TL_sendMessageRecordAudioAction) {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, LocaleController.formatString("IsRecordingAudio", R.string.IsRecordingAudio, getUserNameForTyping(user)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("RecordingAudio", R.string.RecordingAudio));
                    }
                    newPrintingStringsTypes.put(key, 1);
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
                } else {
                    if (lower_id < 0) {
                        newPrintingStrings.put(key, String.format("%s %s", getUserNameForTyping(user), LocaleController.getString("IsTyping", R.string.IsTyping)));
                    } else {
                        newPrintingStrings.put(key, LocaleController.getString("Typing", R.string.Typing));
                    }
                    newPrintingStringsTypes.put(key, 0);
                }
            } else {
                int count = 0;
                String label = "";
                for (PrintingUser pu : arr) {
                    TLRPC.User user = getUser(pu.userId);
                    if (user != null) {
                        if (label.length() != 0) {
                            label += ", ";
                        }
                        label += getUserNameForTyping(user);
                        count++;
                    }
                    if (count == 2) {
                        break;
                    }
                }
                if (label.length() != 0) {
                    if (count == 1) {
                        newPrintingStrings.put(key, String.format("%s %s", label, LocaleController.getString("IsTyping", R.string.IsTyping)));
                    } else {
                        if (arr.size() > 2) {
                            newPrintingStrings.put(key, String.format("%s %s", label, LocaleController.formatPluralString("AndMoreTyping", arr.size() - 2)));
                        } else {
                            newPrintingStrings.put(key, String.format("%s %s", label, LocaleController.getString("AreTyping", R.string.AreTyping)));
                        }
                    }
                    newPrintingStringsTypes.put(key, 0);
                }
            }
        }

        lastPrintingStringCount = newPrintingStrings.size();

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                printingStrings = newPrintingStrings;
                printingStringsTypes = newPrintingStringsTypes;
            }
        });
    }

    public void cancelTyping(int action, long dialog_id) {
        HashMap<Long, Boolean> typings = sendingTypings.get(action);
        if (typings != null) {
            typings.remove(dialog_id);
        }
    }

    public void sendTyping(final long dialog_id, final int action, int classGuid) {
        if (dialog_id == 0) {
            return;
        }
        HashMap<Long, Boolean> typings = sendingTypings.get(action);
        if (typings != null && typings.get(dialog_id) != null) {
            return;
        }
        if (typings == null) {
            typings = new HashMap<>();
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
            }
            typings.put(dialog_id, true);
            int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            HashMap<Long, Boolean> typings = sendingTypings.get(action);
                            if (typings != null) {
                                typings.remove(dialog_id);
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
            if (classGuid != 0) {
                ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
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
                int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                HashMap<Long, Boolean> typings = sendingTypings.get(action);
                                if (typings != null) {
                                    typings.remove(dialog_id);
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
                if (classGuid != 0) {
                    ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
                }
            }
        }
    }

    public void loadMessages(final long dialog_id, final int count, final int max_id, boolean fromCache, int midDate, final int classGuid, final int load_type, final int last_message_id, final int important, final int loadIndex) {
        loadMessages(dialog_id, count, max_id, fromCache, midDate, classGuid, load_type, last_message_id, important, loadIndex, 0, 0, 0, false);
    }

    public void loadMessages(final long dialog_id, final int count, final int max_id, boolean fromCache, int midDate, final int classGuid, final int load_type, final int last_message_id, final int important, final int loadIndex, final int first_unread, final int unread_count, final int last_date, final boolean queryFromServer) {
        FileLog.e("tmessages", "load messages in chat " + dialog_id + " count " + count + " max_id " + max_id + " cache " + fromCache + " mindate = " + midDate + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " imp " + important + " index " + loadIndex + " firstUnread " + first_unread + " underad count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        int lower_part = (int) dialog_id;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance().getMessages(dialog_id, count, max_id, midDate, classGuid, load_type, important, loadIndex);
        } else {
            TLObject request;
            if (important == 2) {
                TLRPC.TL_channels_getImportantHistory req = new TLRPC.TL_channels_getImportantHistory();
                req.channel = getInputChannel(-lower_part);
                if (load_type == 3) {
                    req.add_offset = -count / 2;
                } else if (load_type == 1) {
                    req.add_offset = -count - 1;
                } else if (load_type == 2 && max_id != 0) {
                    req.add_offset = -count + 6;
                } else {
                    if (max_id != 0) {
                        req.add_offset = -1;
                        req.limit += 1;
                    }
                }
                req.limit += count;
                req.offset_id = max_id;
                request = req;
            } else {
                TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
                req.peer = getInputPeer(lower_part);
                if (load_type == 3) {
                    req.add_offset = -count / 2;
                } else if (load_type == 1) {
                    req.add_offset = -count - 1;
                } else if (load_type == 2 && max_id != 0) {
                    req.add_offset = -count + 6;
                } else {
                    req.add_offset = 0;
                }
                req.limit = count;
                req.offset_id = max_id;
                request = req;
            }
            int reqId = ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        if (res.messages.size() > count) {
                            res.messages.remove(0);
                        }
                        processLoadedMessages(res, dialog_id, count, max_id, false, classGuid, first_unread, last_message_id, unread_count, last_date, load_type, important, false, loadIndex, queryFromServer);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
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
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            ArrayList<MessageObject> arrayList = reloadingWebpages.remove(url);
                            if (arrayList == null) {
                                return;
                            }
                            TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                            if (!(response instanceof TLRPC.TL_messageMediaWebPage)) {
                                for (int a = 0; a < arrayList.size(); a++) {
                                    arrayList.get(a).messageOwner.media.webpage = new TLRPC.TL_webPageEmpty();
                                    messagesRes.messages.add(arrayList.get(a).messageOwner);
                                }
                            } else {
                                TLRPC.TL_messageMediaWebPage media = (TLRPC.TL_messageMediaWebPage) response;
                                if (media.webpage instanceof TLRPC.TL_webPage || media.webpage instanceof TLRPC.TL_webPageEmpty) {
                                    for (int a = 0; a < arrayList.size(); a++) {
                                        arrayList.get(a).messageOwner.media.webpage = media.webpage;
                                        if (a == 0) {
                                            ImageLoader.saveMessageThumbs(arrayList.get(a).messageOwner);
                                        }
                                        messagesRes.messages.add(arrayList.get(a).messageOwner);
                                    }
                                } else {
                                    reloadingWebpagesPending.put(media.webpage.id, arrayList);
                                }
                            }
                            if (!messagesRes.messages.isEmpty()) {
                                MessagesStorage.getInstance().putMessages(messagesRes, dialog_id, -2, 0, 0, false);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList);
                            }
                        }
                    });
                }
            });
        }
    }

    public void processLoadedMessages(final TLRPC.messages_Messages messagesRes, final long dialog_id, final int count, final int max_id, final boolean isCache, final int classGuid,
                                      final int first_unread, final int last_message_id, final int unread_count, final int last_date, final int load_type, final int important, final boolean isEnd, final int loadIndex, final boolean queryFromServer) {
        FileLog.e("tmessages", "processLoadedMessages size " + messagesRes.messages.size() + " in chat " + dialog_id + " count " + count + " max_id " + max_id + " cache " + isCache + " guid " + classGuid + " load_type " + load_type + " last_message_id " + last_message_id + " imp " + important + " index " + loadIndex + " firstUnread " + first_unread + " underad count " + unread_count + " last_date " + last_date + " queryFromServer " + queryFromServer);
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                boolean createDialog = false;
                boolean isMegagroup = false;
                if (messagesRes instanceof TLRPC.TL_messages_channelMessages) {
                    int channelId = -(int) dialog_id;
                    Integer channelPts = channelsPts.get(channelId);
                    if (channelPts == null) {
                        channelPts = MessagesStorage.getInstance().getChannelPtsSync(channelId);
                        if (channelPts == 0) {
                            channelsPts.put(channelId, messagesRes.pts);
                            createDialog = true;
                            if (needShortPollChannels.indexOfKey(channelId) >= 0 && shortPollChannels.indexOfKey(channelId) < 0) {
                                getChannelDifference(channelId, 2);
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
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadMessages(dialog_id, count, load_type == 2 && queryFromServer ? first_unread : max_id, false, 0, classGuid, load_type, last_message_id, important, loadIndex, first_unread, unread_count, last_date, queryFromServer);
                        }
                    });
                    return;
                }
                final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();
                final HashMap<Integer, TLRPC.Chat> chatsDict = new HashMap<>();
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
                    for (int a = 0; a < size; a++) {
                        TLRPC.Message message = messagesRes.messages.get(a);
                        if (!isCache && message.post && !message.out) {
                            message.media_unread = true;
                        }
                        if (isMegagroup) {
                            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                        }
                        if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                            TLRPC.User user = usersDict.get(message.action.user_id);
                            if (user != null && user.bot) {
                                message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                            }
                        } else if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                            message.unread = false;
                            message.media_unread = false;
                        }
                    }
                    MessagesStorage.getInstance().putMessages(messagesRes, dialog_id, load_type, max_id, important, createDialog);
                }
                final ArrayList<MessageObject> objects = new ArrayList<>();
                final ArrayList<Integer> messagesToReload = new ArrayList<>();
                final HashMap<String, ArrayList<MessageObject>> webpagesToReload = new HashMap<>();
                TLRPC.InputChannel inputChannel = null;
                for (int a = 0; a < size; a++) {
                    TLRPC.Message message = messagesRes.messages.get(a);
                    message.dialog_id = dialog_id;
                    MessageObject messageObject = new MessageObject(message, usersDict, chatsDict, true);
                    objects.add(messageObject);
                    if (isCache) {
                        if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                            if (message.media.bytes != null && (message.media.bytes.length == 0 || message.media.bytes.length == 1 && message.media.bytes[0] < TLRPC.LAYER)) {
                                messagesToReload.add(message.id);
                            }
                        } else if (message.media instanceof TLRPC.TL_messageMediaWebPage) {
                            if (message.media.webpage instanceof TLRPC.TL_webPagePending && message.media.webpage.date <= ConnectionsManager.getInstance().getCurrentTime()) {
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
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
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
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesDidLoaded, dialog_id, count, objects, isCache, first_unread_final, last_message_id, unread_count, last_date, load_type, messagesRes.collapsed, isEnd, classGuid, loadIndex);
                        if (!messagesToReload.isEmpty()) {
                            reloadMessages(messagesToReload, dialog_id);
                        }
                        if (!webpagesToReload.isEmpty()) {
                            reloadWebPages(dialog_id, webpagesToReload);
                        }
                    }
                });
            }
        });
    }

    public void loadDialogs(final int offset, final int count, boolean fromCache) {
        if (loadingDialogs) {
            return;
        }
        loadingDialogs = true;
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        FileLog.e("tmessages", "load cacheOffset = " + offset + " count = " + count + " cache = " + fromCache);
        if (fromCache) {
            MessagesStorage.getInstance().getDialogs(offset == 0 ? 0 : nextDialogsCacheOffset, count);
        } else {
            TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
            req.limit = count;
            boolean found = false;
            for (int a = dialogs.size() - 1; a >= 0; a--) {
                TLRPC.Dialog dialog = dialogs.get(a);
                int lower_id = (int) dialog.id;
                int high_id = (int) (dialog.id >> 32);
                if (lower_id != 0 && high_id != 1 && dialog.top_message > 0) {
                    MessageObject message = dialogMessage.get(dialog.id);
                    if (message != null && message.getId() > 0) {
                        req.offset_date = Math.max(dialog.last_message_date_i, message.messageOwner.date);
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
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                        processLoadedDialogs(dialogsRes, null, 0, count, false, false, false);
                    }
                }
            });
        }
    }

    private void migrateDialogs(final int offset, final int offsetDate, final int offsetUser, final int offsetChat, final int offsetChannel, final long accessPeer) {
        if (migratingDialogs || offset == -1) {
            return;
        }
        migratingDialogs = true;

        TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
        req.limit = 100;
        req.offset_id = offset;
        req.offset_date = offsetDate;
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
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                int offsetId;
                                if (dialogsRes.dialogs.size() == 100) {
                                    TLRPC.Message lastMessage = null;
                                    for (int a = 0; a < dialogsRes.messages.size(); a++) {
                                        TLRPC.Message message = dialogsRes.messages.get(a);
                                        if (lastMessage == null || message.date < lastMessage.date) {
                                            lastMessage = message;
                                        }
                                    }
                                    offsetId = lastMessage.id;
                                    UserConfig.migrateOffsetDate = lastMessage.date;
                                    if (lastMessage.to_id.channel_id != 0) {
                                        UserConfig.migrateOffsetChannelId = lastMessage.to_id.channel_id;
                                        UserConfig.migrateOffsetChatId = 0;
                                        UserConfig.migrateOffsetUserId = 0;
                                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                                            if (chat.id == UserConfig.migrateOffsetChannelId) {
                                                UserConfig.migrateOffsetAccess = chat.access_hash;
                                                break;
                                            }
                                        }
                                    } else if (lastMessage.to_id.chat_id != 0) {
                                        UserConfig.migrateOffsetChatId = lastMessage.to_id.chat_id;
                                        UserConfig.migrateOffsetChannelId = 0;
                                        UserConfig.migrateOffsetUserId = 0;
                                        for (int a = 0; a < dialogsRes.chats.size(); a++) {
                                            TLRPC.Chat chat = dialogsRes.chats.get(a);
                                            if (chat.id == UserConfig.migrateOffsetChatId) {
                                                UserConfig.migrateOffsetAccess = chat.access_hash;
                                                break;
                                            }
                                        }
                                    } else if (lastMessage.to_id.user_id != 0) {
                                        UserConfig.migrateOffsetUserId = lastMessage.to_id.user_id;
                                        UserConfig.migrateOffsetChatId = 0;
                                        UserConfig.migrateOffsetChannelId = 0;
                                        for (int a = 0; a < dialogsRes.users.size(); a++) {
                                            TLRPC.User user = dialogsRes.users.get(a);
                                            if (user.id == UserConfig.migrateOffsetUserId) {
                                                UserConfig.migrateOffsetAccess = user.access_hash;
                                                break;
                                            }
                                        }
                                    }
                                } else {
                                    offsetId = -1;
                                }

                                StringBuilder dids = new StringBuilder(dialogsRes.dialogs.size() * 12);
                                HashMap<Long, TLRPC.Dialog> dialogHashMap = new HashMap<>();
                                for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                                    TLRPC.Dialog dialog = dialogsRes.dialogs.get(a);
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
                                SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT did FROM dialogs WHERE did IN (%s)", dids.toString()));
                                while (cursor.next()) {
                                    long did = cursor.longValue(0);
                                    TLRPC.Dialog dialog = dialogHashMap.remove(did);
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
                                            }
                                            if (message.id == dialog.top_not_important_message) {
                                                dialog.top_not_important_message = 0;
                                            }
                                            if (dialog.top_message == 0 && dialog.top_not_important_message == 0) {
                                                break;
                                            }
                                        }
                                    }
                                }
                                cursor.dispose();
                                cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT min(date) FROM dialogs WHERE date != 0 AND did >> 32 IN (0, -1)");
                                if (cursor.next()) {
                                    int date = Math.max(1441062000, cursor.intValue(0));
                                    for (int a = 0; a < dialogsRes.messages.size(); a++) {
                                        TLRPC.Message message = dialogsRes.messages.get(a);
                                        if (message.date < date) {
                                            offsetId = -1;
                                            dialogsRes.messages.remove(a);
                                            a--;
                                            TLRPC.Dialog dialog = dialogHashMap.remove(MessageObject.getDialogId(message));
                                            if (dialog != null) {
                                                dialogsRes.dialogs.remove(dialog);
                                            }
                                        }
                                    }
                                }
                                cursor.dispose();

                                processLoadedDialogs(dialogsRes, null, offsetId, 0, false, false, true);
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                                AndroidUtilities.runOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        migratingDialogs = false;
                                    }
                                });
                            }
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            migratingDialogs = false;
                        }
                    });
                }
            }
        });

    }

    public void processLoadedDialogs(final TLRPC.messages_Dialogs dialogsRes, final ArrayList<TLRPC.EncryptedChat> encChats, final int offset, final int count, final boolean isCache, final boolean resetEnd, final boolean migrate) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileLog.e("tmessages", "loaded from " + isCache + " count " + dialogsRes.dialogs.size());
                if (isCache && dialogsRes.dialogs.size() == 0) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(dialogsRes.users, true);
                            loadingDialogs = false;
                            if (resetEnd) {
                                dialogsEndReached = false;
                            }
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            loadDialogs(0, count, false);
                        }
                    });
                    return;
                }

                final HashMap<Long, TLRPC.Dialog> new_dialogs_dict = new HashMap<>();
                final HashMap<Long, MessageObject> new_dialogMessage = new HashMap<>();
                final HashMap<Integer, Integer> notImportantDates = new HashMap<>();
                final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();
                final HashMap<Integer, TLRPC.Chat> chatsDict = new HashMap<>();

                for (int a = 0; a < dialogsRes.users.size(); a++) {
                    TLRPC.User u = dialogsRes.users.get(a);
                    usersDict.put(u.id, u);
                }
                for (int a = 0; a < dialogsRes.chats.size(); a++) {
                    TLRPC.Chat c = dialogsRes.chats.get(a);
                    chatsDict.put(c.id, c);
                }
                if (isCache) {
                    nextDialogsCacheOffset = offset + count;
                }

                for (int a = 0; a < dialogsRes.messages.size(); a++) {
                    TLRPC.Message message = dialogsRes.messages.get(a);
                    if (message.to_id.channel_id != 0) {
                        if (!MessageObject.isImportant(message)) {
                            notImportantDates.put(-message.to_id.channel_id, message.date);
                        }
                        TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                        if (chat != null && chat.left/* && !chat.megagroup*/) {
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
                    if (!isCache && message.post && !message.out) {
                        message.media_unread = true;
                    }
                    MessageObject messageObject = new MessageObject(message, usersDict, chatsDict, false);
                    MessageObject currentMessage = new_dialogMessage.get(messageObject.getDialogId());
                    if (currentMessage == null || messageObject.isMegagroup() || messageObject.isImportant()) {
                        new_dialogMessage.put(messageObject.getDialogId(), messageObject);
                    }
                }

                for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                    TLRPC.Dialog d = dialogsRes.dialogs.get(a);
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
                    if (d.last_message_date_i == 0 && d.top_not_important_message != 0) {
                        Integer date = notImportantDates.get((int) d.id);
                        if (date != null) {
                            d.last_message_date_i = date;
                        }
                    }
                    if (d instanceof TLRPC.TL_dialogChannel) {
                        TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                        if (chat != null && chat.megagroup) {
                            d.top_message = Math.max(d.top_message, d.top_not_important_message);
                            d.unread_count = Math.max(d.unread_count, d.unread_not_important_count);
                        }
                        if (chat != null && chat.left/* && !chat.megagroup*/) {
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
                }

                if (!isCache) {
                    ImageLoader.saveMessagesThumbs(dialogsRes.messages);

                    for (int a = 0; a < dialogsRes.messages.size(); a++) {
                        TLRPC.Message message = dialogsRes.messages.get(a);
                        if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                            TLRPC.User user = usersDict.get(message.action.user_id);
                            if (user != null && user.bot) {
                                message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                            }
                        } else if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                            message.unread = false;
                            message.media_unread = false;
                        }
                    }
                    MessagesStorage.getInstance().putDialogs(dialogsRes);
                }

                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCache) {
                            applyDialogsNotificationsSettings(dialogsRes.dialogs);
                        }
                        putUsers(dialogsRes.users, isCache);
                        putChats(dialogsRes.chats, isCache);
                        if (encChats != null) {
                            for (int a = 0; a < encChats.size(); a++) {
                                TLRPC.EncryptedChat encryptedChat = encChats.get(a);
                                if (encryptedChat instanceof TLRPC.TL_encryptedChat && AndroidUtilities.getMyLayerVersion(encryptedChat.layer) < SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
                                    SecretChatHelper.getInstance().sendNotifyLayerMessage(encryptedChat, null);
                                }
                                putEncryptedChat(encryptedChat, true);
                            }
                        }
                        if (!migrate) {
                            loadingDialogs = false;
                        }
                        boolean added = false;

                        int lastDialogDate = migrate && !dialogs.isEmpty() ? dialogs.get(dialogs.size() - 1).last_message_date : 0;
                        for (HashMap.Entry<Long, TLRPC.Dialog> pair : new_dialogs_dict.entrySet()) {
                            Long key = pair.getKey();
                            TLRPC.Dialog value = pair.getValue();
                            if (migrate && lastDialogDate != 0 && value.last_message_date < lastDialogDate) {
                                continue;
                            }
                            TLRPC.Dialog currentDialog = dialogs_dict.get(key);
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
                                if (!isCache) {
                                    currentDialog.notify_settings = value.notify_settings;
                                }
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
                        dialogsServerOnly.clear();
                        dialogsGroupsOnly.clear();
                        dialogs.addAll(dialogs_dict.values());
                        Collections.sort(dialogs, new Comparator<TLRPC.Dialog>() {
                            @Override
                            public int compare(TLRPC.Dialog tl_dialog, TLRPC.Dialog tl_dialog2) {
                                if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                    return 0;
                                } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }
                        });
                        for (int a = 0; a < dialogs.size(); a++) {
                            TLRPC.Dialog d = dialogs.get(a);

                            int high_id = (int) (d.id >> 32);
                            if ((int) d.id != 0 && high_id != 1) {
                                if (d instanceof TLRPC.TL_dialog) {
                                    if (d.id < 0) {
                                        if (migrate) {
                                            TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                                            if (chat != null && chat.migrated_to != null) {
                                                dialogs.remove(a);
                                                a--;
                                                continue;
                                            }
                                        }
                                        dialogsGroupsOnly.add(d);
                                    }
                                } else if (d instanceof TLRPC.TL_dialogChannel) {
                                    int lower_id = (int) d.id;
                                    TLRPC.Chat chat = getChat(-lower_id);
                                    if (chat != null && (chat.megagroup && chat.editor || chat.creator)) {
                                        dialogsGroupsOnly.add(d);
                                    }
                                }
                                dialogsServerOnly.add(d);
                            }
                        }

                        if (!migrate) {
                            dialogsEndReached = (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && !isCache;
                        }
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);

                        if (migrate) {
                            UserConfig.migrateOffsetId = offset;
                            UserConfig.saveConfig(false);
                            migratingDialogs = false;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
                        } else {
                            generateUpdateMessage();
                            if (!added && isCache) {
                                loadDialogs(0, count, false);
                            }
                        }
                        migrateDialogs(UserConfig.migrateOffsetId, UserConfig.migrateOffsetDate, UserConfig.migrateOffsetUserId, UserConfig.migrateOffsetChatId, UserConfig.migrateOffsetChannelId, UserConfig.migrateOffsetAccess);
                    }
                });
            }
        });
    }

    private void applyDialogNotificationsSettings(long dialog_id, TLRPC.PeerNotifySettings notify_settings) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        int currentValue = preferences.getInt("notify2_" + dialog_id, 0);
        int currentValue2 = preferences.getInt("notifyuntil_" + dialog_id, 0);
        SharedPreferences.Editor editor = preferences.edit();
        boolean updated = false;
        TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
        if (dialog != null) {
            dialog.notify_settings = notify_settings;
        }
        editor.putBoolean("silent_" + dialog_id, notify_settings.silent);
        if (notify_settings.mute_until > ConnectionsManager.getInstance().getCurrentTime()) {
            int until = 0;
            if (notify_settings.mute_until > ConnectionsManager.getInstance().getCurrentTime() + 60 * 60 * 24 * 365) {
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
                    until = notify_settings.mute_until;
                    editor.putInt("notify2_" + dialog_id, 3);
                    editor.putInt("notifyuntil_" + dialog_id, notify_settings.mute_until);
                    if (dialog != null) {
                        dialog.notify_settings.mute_until = until;
                    }
                }
            }
            MessagesStorage.getInstance().setDialogFlags(dialog_id, ((long) until << 32) | 1);
            NotificationsController.getInstance().removeNotificationsForDialog(dialog_id);
        } else {
            if (currentValue != 0 && currentValue != 1) {
                updated = true;
                if (dialog != null) {
                    dialog.notify_settings.mute_until = 0;
                }
                editor.remove("notify2_" + dialog_id);
            }
            MessagesStorage.getInstance().setDialogFlags(dialog_id, 0);
        }
        editor.commit();
        if (updated) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
        }
    }

    private void applyDialogsNotificationsSettings(ArrayList<TLRPC.Dialog> dialogs) {
        SharedPreferences.Editor editor = null;
        for (int a = 0; a < dialogs.size(); a++) {
            TLRPC.Dialog dialog = dialogs.get(a);
            if (dialog.peer != null && dialog.notify_settings instanceof TLRPC.TL_peerNotifySettings) {
                if (editor == null) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    editor = preferences.edit();
                }
                int dialog_id;
                if (dialog.peer.user_id != 0) {
                    dialog_id = dialog.peer.user_id;
                } else if (dialog.peer.chat_id != 0) {
                    dialog_id = -dialog.peer.chat_id;
                } else {
                    dialog_id = -dialog.peer.channel_id;
                }
                editor.putBoolean("silent_" + dialog_id, dialog.notify_settings.silent);
                if (dialog.notify_settings.mute_until != 0) {
                    if (dialog.notify_settings.mute_until > ConnectionsManager.getInstance().getCurrentTime() + 60 * 60 * 24 * 365) {
                        editor.putInt("notify2_" + dialog_id, 2);
                        dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                    } else {
                        editor.putInt("notify2_" + dialog_id, 3);
                        editor.putInt("notifyuntil_" + dialog_id, dialog.notify_settings.mute_until);
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

    public void processDialogsUpdateRead(final HashMap<Long, Integer> dialogsToUpdate) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                for (HashMap.Entry<Long, Integer> entry : dialogsToUpdate.entrySet()) {
                    TLRPC.Dialog currentDialog = dialogs_dict.get(entry.getKey());
                    if (currentDialog != null) {
                        currentDialog.unread_count = entry.getValue();
                    }
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
            }
        });
    }

    public void processDialogsUpdate(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final HashMap<Long, TLRPC.Dialog> new_dialogs_dict = new HashMap<>();
                final HashMap<Long, MessageObject> new_dialogMessage = new HashMap<>();
                final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();
                final HashMap<Integer, TLRPC.Chat> chatsDict = new HashMap<>();
                final HashMap<Long, Integer> dialogsToUpdate = new HashMap<>();

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
                    if (message.to_id.channel_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.to_id.channel_id);
                        if (chat != null && chat.left/* && !chat.megagroup*/) {
                            continue;
                        }
                    } else if (message.to_id.chat_id != 0) {
                        TLRPC.Chat chat = chatsDict.get(message.to_id.chat_id);
                        if (chat != null && chat.migrated_to != null) {
                            continue;
                        }
                    }
                    MessageObject messageObject = new MessageObject(message, usersDict, chatsDict, false);
                    new_dialogMessage.put(messageObject.getDialogId(), messageObject);
                }
                for (int a = 0; a < dialogsRes.dialogs.size(); a++) {
                    TLRPC.Dialog d = dialogsRes.dialogs.get(a);
                    if (d.id == 0) {
                        if (d.peer.user_id != 0) {
                            d.id = d.peer.user_id;
                        } else if (d.peer.chat_id != 0) {
                            d.id = -d.peer.chat_id;
                        } else if (d.peer.channel_id != 0) {
                            d.id = -d.peer.channel_id;
                        }
                    }
                    if (d instanceof TLRPC.TL_dialogChannel) {
                        TLRPC.Chat chat = chatsDict.get(-(int) d.id);
                        if (chat != null && chat.left/* && !chat.megagroup*/) {
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
                    new_dialogs_dict.put(d.id, d);
                    dialogsToUpdate.put(d.id, d.unread_count);

                    Integer value = dialogs_read_inbox_max.get(d.id);
                    if (value == null) {
                        value = 0;
                    }
                    dialogs_read_inbox_max.put(d.id, Math.max(value, d.read_inbox_max_id));
                }

                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        putUsers(dialogsRes.users, true);
                        putChats(dialogsRes.chats, true);

                        for (HashMap.Entry<Long, TLRPC.Dialog> pair : new_dialogs_dict.entrySet()) {
                            Long key = pair.getKey();
                            TLRPC.Dialog value = pair.getValue();
                            TLRPC.Dialog currentDialog = dialogs_dict.get(key);
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
                        dialogsServerOnly.clear();
                        dialogsGroupsOnly.clear();
                        dialogs.addAll(dialogs_dict.values());
                        Collections.sort(dialogs, new Comparator<TLRPC.Dialog>() {
                            @Override
                            public int compare(TLRPC.Dialog dialog, TLRPC.Dialog dialog2) {
                                if (dialog.last_message_date == dialog2.last_message_date) {
                                    return 0;
                                } else if (dialog.last_message_date < dialog2.last_message_date) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }
                        });
                        for (int a = 0; a < dialogs.size(); a++) {
                            TLRPC.Dialog d = dialogs.get(a);
                            int high_id = (int) (d.id >> 32);
                            if ((int) d.id != 0 && high_id != 1) {
                                dialogsServerOnly.add(d);
                                if (d instanceof TLRPC.TL_dialog) {
                                    if (d.id < 0) {
                                        dialogsGroupsOnly.add(d);
                                    }
                                } else if (d instanceof TLRPC.TL_dialogChannel) {
                                    int lower_id = (int) d.id;
                                    TLRPC.Chat chat = getChat(-lower_id);
                                    if (chat != null && (chat.megagroup && chat.editor || chat.creator)) {
                                        dialogsGroupsOnly.add(d);
                                    }
                                }
                            }
                        }
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                        NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                    }
                });
            }
        });
    }

    public void addToViewsQueue(final TLRPC.Message message, final boolean reload) {
        ArrayList<Long> arrayList = new ArrayList<>();
        long messageId = message.id;
        if (message.to_id.channel_id != 0) {
            messageId |= ((long) message.to_id.channel_id) << 32;
        }
        arrayList.add(messageId);
        MessagesStorage.getInstance().markMessagesContentAsRead(arrayList);
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                SparseArray<ArrayList<Integer>> array = channelViewsToSend;//reload ? channelViewsToReload : channelViewsToSend;
                int peer;
                if (message.to_id.channel_id != 0) {
                    peer = -message.to_id.channel_id;
                } else if (message.to_id.chat_id != 0) {
                    peer = -message.to_id.chat_id;
                } else {
                    peer = message.to_id.user_id;
                }
                ArrayList<Integer> ids = array.get(peer);
                if (ids == null) {
                    ids = new ArrayList<>();
                    array.put(peer, ids);
                }
                if (!ids.contains(message.id)) {
                    ids.add(message.id);
                }
            }
        });
    }

    public void markMessageContentAsRead(final MessageObject messageObject) {
        ArrayList<Long> arrayList = new ArrayList<>();
        long messageId = messageObject.getId();
        if (messageObject.messageOwner.to_id.channel_id != 0) {
            messageId |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
        }
        arrayList.add(messageId);
        MessagesStorage.getInstance().markMessagesContentAsRead(arrayList);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesReadContent, arrayList);
        if (messageObject.getId() < 0) {
            markMessageAsRead(messageObject.getDialogId(), messageObject.messageOwner.random_id, Integer.MIN_VALUE);
        } else {
            TLRPC.TL_messages_readMessageContents req = new TLRPC.TL_messages_readMessageContents();
            req.id.add(messageObject.getId());
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                        processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    }
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
        SecretChatHelper.getInstance().sendMessagesReadMessage(chat, random_ids, null);
        if (ttl > 0) {
            int time = ConnectionsManager.getInstance().getCurrentTime();
            MessagesStorage.getInstance().createTaskForSecretChat(chat.id, time, time, 0, random_ids);
        }
    }

    public void markDialogAsRead(final long dialog_id, final int max_id, final int max_positive_id, final int max_date, final boolean was, final boolean popup) {
        int lower_part = (int) dialog_id;
        int high_id = (int) (dialog_id >> 32);

        if (lower_part != 0) {
            if (max_positive_id == 0 || high_id == 1) {
                return;
            }
            TLRPC.InputPeer inputPeer = getInputPeer(lower_part);
            TLObject req;
            long messageId = max_positive_id;
            if (inputPeer instanceof TLRPC.TL_inputPeerChannel) {
                TLRPC.TL_channels_readHistory request = new TLRPC.TL_channels_readHistory();
                request.channel = getInputChannel(-lower_part);
                request.max_id = max_positive_id;
                req = request;
                messageId |= ((long) -lower_part) << 32;
            } else {
                TLRPC.TL_messages_readHistory request = new TLRPC.TL_messages_readHistory();
                request.peer = inputPeer;
                request.max_id = max_positive_id;
                req = request;
            }
            Integer value = dialogs_read_inbox_max.get(dialog_id);
            if (value == null) {
                value = 0;
            }
            dialogs_read_inbox_max.put(dialog_id, Math.max(value, max_positive_id));

            MessagesStorage.getInstance().processPendingRead(dialog_id, messageId, max_date, false);
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
                            if (dialog != null) {
                                dialog.unread_count = 0;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                            }
                            if (!popup) {
                                NotificationsController.getInstance().processReadMessages(null, dialog_id, 0, max_positive_id, false);
                                HashMap<Long, Integer> dialogsToUpdate = new HashMap<>();
                                dialogsToUpdate.put(dialog_id, 0);
                                NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                            } else {
                                NotificationsController.getInstance().processReadMessages(null, dialog_id, 0, max_positive_id, true);
                                HashMap<Long, Integer> dialogsToUpdate = new HashMap<>();
                                dialogsToUpdate.put(dialog_id, -1);
                                NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                            }
                        }
                    });
                }
            });

            if (max_positive_id != Integer.MAX_VALUE) {
                final long messageIdFinal = messageId;
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            MessagesStorage.getInstance().processPendingRead(dialog_id, messageIdFinal, max_date, true);
                            if (response instanceof TLRPC.TL_messages_affectedMessages) {
                                TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                                processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                            }
                        }
                    }
                });
            }
        } else {
            if (max_date == 0) {
                return;
            }
            TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_readEncryptedHistory req = new TLRPC.TL_messages_readEncryptedHistory();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.max_date = max_date;

                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        //MessagesStorage.getInstance().processPendingRead(dialog_id, max_id, max_date, true);
                    }
                });
            }
            MessagesStorage.getInstance().processPendingRead(dialog_id, max_id, max_date, false);

            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationsController.getInstance().processReadMessages(null, dialog_id, max_date, 0, popup);
                            TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
                            if (dialog != null) {
                                dialog.unread_count = 0;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                            }
                            HashMap<Long, Integer> dialogsToUpdate = new HashMap<>();
                            dialogsToUpdate.put(dialog_id, 0);
                            NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                        }
                    });
                }
            });

            if (chat.ttl > 0 && was) {
                int serverTime = Math.max(ConnectionsManager.getInstance().getCurrentTime(), max_date);
                MessagesStorage.getInstance().createTaskForSecretChat(chat.id, serverTime, serverTime, 0, null);
            }
        }
    }

    public int createChat(String title, ArrayList<Integer> selectedContacts, final String about, int type, final BaseFragment fragment) {
        if (type == ChatObject.CHAT_TYPE_BROADCAST) {
            TLRPC.TL_chat chat = new TLRPC.TL_chat();
            chat.id = UserConfig.lastBroadcastId;
            chat.title = title;
            chat.photo = new TLRPC.TL_chatPhotoEmpty();
            chat.participants_count = selectedContacts.size();
            chat.date = (int) (System.currentTimeMillis() / 1000);
            chat.version = 1;
            UserConfig.lastBroadcastId--;
            putChat(chat, false);
            ArrayList<TLRPC.Chat> chatsArrays = new ArrayList<>();
            chatsArrays.add(chat);
            MessagesStorage.getInstance().putUsersAndChats(null, chatsArrays, true, true);

            TLRPC.TL_chatFull chatFull = new TLRPC.TL_chatFull();
            chatFull.id = chat.id;
            chatFull.chat_photo = new TLRPC.TL_photoEmpty();
            chatFull.notify_settings = new TLRPC.TL_peerNotifySettingsEmpty();
            chatFull.exported_invite = new TLRPC.TL_chatInviteEmpty();
            chatFull.participants = new TLRPC.TL_chatParticipants();
            chatFull.participants.chat_id = chat.id;
            chatFull.participants.admin_id = UserConfig.getClientUserId();
            chatFull.participants.version = 1;
            for (int a = 0; a < selectedContacts.size(); a++) {
                TLRPC.TL_chatParticipant participant = new TLRPC.TL_chatParticipant();
                participant.user_id = selectedContacts.get(a);
                participant.inviter_id = UserConfig.getClientUserId();
                participant.date = (int) (System.currentTimeMillis() / 1000);
                chatFull.participants.participants.add(participant);
            }
            MessagesStorage.getInstance().updateChatInfo(chatFull, false);

            TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();
            newMsg.action = new TLRPC.TL_messageActionCreatedBroadcastList();
            newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
            newMsg.from_id = UserConfig.getClientUserId();
            newMsg.dialog_id = AndroidUtilities.makeBroadcastId(chat.id);
            newMsg.to_id = new TLRPC.TL_peerChat();
            newMsg.to_id.chat_id = chat.id;
            newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
            newMsg.random_id = 0;
            newMsg.flags |= TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
            UserConfig.saveConfig(false);
            MessageObject newMsgObj = new MessageObject(newMsg, users, true);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;

            ArrayList<MessageObject> objArr = new ArrayList<>();
            objArr.add(newMsgObj);
            ArrayList<TLRPC.Message> arr = new ArrayList<>();
            arr.add(newMsg);
            MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
            updateInterfaceWithMessages(newMsg.dialog_id, objArr);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidCreated, chat.id);

            return 0;
        } else if (type == ChatObject.CHAT_TYPE_CHAT) {
            TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
            req.title = title;
            for (int a = 0; a < selectedContacts.size(); a++) {
                TLRPC.User user = getUser(selectedContacts.get(a));
                if (user == null) {
                    continue;
                }
                req.users.add(getInputUser(user));
            }
            return ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, final TLRPC.TL_error error) {
                    if (error != null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (error.text.startsWith("FLOOD_WAIT")) {
                                    AlertsCreator.showFloodWaitAlert(error.text, fragment);
                                } else {
                                    AlertsCreator.showAddUserAlert(error.text, fragment, false);
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidFailCreate);
                            }
                        });
                        return;
                    }
                    final TLRPC.Updates updates = (TLRPC.Updates) response;
                    processUpdates(updates, false);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(updates.users, false);
                            putChats(updates.chats, false);
                            if (updates.chats != null && !updates.chats.isEmpty()) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidCreated, updates.chats.get(0).id);
                            } else {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidFailCreate);
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        } else if (type == ChatObject.CHAT_TYPE_CHANNEL || type == ChatObject.CHAT_TYPE_MEGAGROUP) {
            TLRPC.TL_channels_createChannel req = new TLRPC.TL_channels_createChannel();
            req.title = title;
            req.about = about;
            if (type == ChatObject.CHAT_TYPE_MEGAGROUP) {
                req.megagroup = true;
            } else {
                req.broadcast = true;
            }
            return ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, final TLRPC.TL_error error) {
                    if (error != null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (error.text.startsWith("FLOOD_WAIT")) {
                                    AlertsCreator.showFloodWaitAlert(error.text, fragment);
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidFailCreate);
                            }
                        });
                        return;
                    }
                    final TLRPC.Updates updates = (TLRPC.Updates) response;
                    processUpdates(updates, false);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(updates.users, false);
                            putChats(updates.chats, false);
                            if (updates.chats != null && !updates.chats.isEmpty()) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidCreated, updates.chats.get(0).id);
                            } else {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidFailCreate);
                            }
                        }
                    });
                }
            }, ConnectionsManager.RequestFlagFailOnServerErrors);
        }
        return 0;
    }

    public void convertToMegaGroup(final Context context, int chat_id) {
        TLRPC.TL_messages_migrateChat req = new TLRPC.TL_messages_migrateChat();
        req.chat_id = chat_id;
        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        final int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!((Activity) context).isFinishing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                            }
                        }
                    });
                    TLRPC.Updates updates = (TLRPC.Updates) response;
                    processUpdates((TLRPC.Updates) response, false);
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!((Activity) context).isFinishing()) {
                                try {
                                    progressDialog.dismiss();
                                } catch (Exception e) {
                                    FileLog.e("tmessages", e);
                                }
                                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
                                builder.setMessage(LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred));
                                builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
                                builder.show().setCanceledOnTouchOutside(true);
                            }
                        }
                    });
                }
            }
        });
        progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                ConnectionsManager.getInstance().cancelRequest(reqId, true);
                try {
                    dialog.dismiss();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
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
        TLRPC.TL_channels_inviteToChannel req = new TLRPC.TL_channels_inviteToChannel();
        req.channel = getInputChannel(chat_id);
        req.users = users;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, final TLRPC.TL_error error) {
                if (error != null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (fragment != null) {
                                AlertsCreator.showAddUserAlert(error.text, fragment, true);
                            } else if (error.text.equals("PEER_FLOOD")) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.needShowAlert, 1);
                            }
                        }
                    });
                    return;
                }
                processUpdates((TLRPC.Updates) response, false);
            }
        });
    }

    public void toogleChannelInvites(int chat_id, boolean enabled) {
        TLRPC.TL_channels_toggleInvites req = new TLRPC.TL_channels_toggleInvites();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (response != null) {
                    processUpdates((TLRPC.Updates) response, false);
                }
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void toogleChannelComments(int chat_id, boolean enabled) {
        TLRPC.TL_channels_toggleComments req = new TLRPC.TL_channels_toggleComments();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (response != null) {
                    processUpdates((TLRPC.Updates) response, false);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHANNEL);
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void toogleChannelSignatures(int chat_id, boolean enabled) {
        TLRPC.TL_channels_toggleSignatures req = new TLRPC.TL_channels_toggleSignatures();
        req.channel = getInputChannel(chat_id);
        req.enabled = enabled;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (response != null) {
                    processUpdates((TLRPC.Updates) response, false);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHANNEL);
                        }
                    });
                }
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
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (response instanceof TLRPC.TL_boolTrue) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            info.about = about;
                            MessagesStorage.getInstance().updateChatInfo(info, false);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, false, null);
                        }
                    });
                }
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void updateChannelUserName(final int chat_id, final String userName) {
        TLRPC.TL_channels_updateUsername req = new TLRPC.TL_channels_updateUsername();
        req.channel = getInputChannel(chat_id);
        req.username = userName;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (response instanceof TLRPC.TL_boolTrue) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            TLRPC.Chat chat = getChat(chat_id);
                            if (userName.length() != 0) {
                                chat.flags |= TLRPC.CHAT_FLAG_IS_PUBLIC;
                            } else {
                                chat.flags &= ~TLRPC.CHAT_FLAG_IS_PUBLIC;
                            }
                            chat.username = userName;
                            ArrayList<TLRPC.Chat> arrayList = new ArrayList<>();
                            arrayList.add(chat);
                            MessagesStorage.getInstance().putUsersAndChats(null, arrayList, true, true);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHANNEL);
                        }
                    });
                }
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
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                processUpdates((TLRPC.Updates) response, false);
            }
        });
    }

    public void toggleAdminMode(final int chat_id, boolean enabled) {
        TLRPC.TL_messages_toggleChatAdmins req = new TLRPC.TL_messages_toggleChatAdmins();
        req.chat_id = chat_id;
        req.enabled = enabled;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    processUpdates((TLRPC.Updates) response, false);
                    loadFullChat(chat_id, 0, true);
                }
            }
        });
    }

    public void toggleUserAdmin(final int chat_id, int user_id, boolean admin) {
        TLRPC.TL_messages_editChatAdmin req = new TLRPC.TL_messages_editChatAdmin();
        req.chat_id = chat_id;
        req.user_id = getInputUser(user_id);
        req.is_admin = admin;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void addUserToChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info, int count_fwd, String botHash, final BaseFragment fragment) {
        if (user == null) {
            return;
        }

        if (chat_id > 0) {
            TLObject request;

            final boolean isChannel = ChatObject.isChannel(chat_id);
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
                        if (user.bot && !isMegagroup) {
                            TLRPC.TL_channels_editAdmin req = new TLRPC.TL_channels_editAdmin();
                            req.channel = getInputChannel(chat_id);
                            req.user_id = getInputUser(user);
                            req.role = new TLRPC.TL_channelRoleEditor();
                            request = req;
                        } else {
                            TLRPC.TL_channels_inviteToChannel req = new TLRPC.TL_channels_inviteToChannel();
                            req.channel = getInputChannel(chat_id);
                            req.users.add(inputUser);
                            request = req;
                        }
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

            ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
                @Override
                public void run(TLObject response, final TLRPC.TL_error error) {
                    if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                joiningToChannels.remove((Integer) chat_id);
                            }
                        });
                    }
                    if (error != null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (fragment != null) {
                                    AlertsCreator.showAddUserAlert(error.text, fragment, isChannel && !isMegagroup);
                                } else {
                                    if (error.text.equals("PEER_FLOOD")) {
                                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.needShowAlert, 1);
                                    }
                                }
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
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                loadFullChat(chat_id, 0, true);
                            }
                        }, 1000);
                    }
                    if (isChannel && inputUser instanceof TLRPC.TL_inputUserSelf) {
                        MessagesStorage.getInstance().updateDialogsWithDeletedMessages(new ArrayList<Integer>(), true, chat_id);
                    }
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
                MessagesStorage.getInstance().putUsersAndChats(null, chatArrayList, true, true);

                TLRPC.TL_chatParticipant newPart = new TLRPC.TL_chatParticipant();
                newPart.user_id = user.id;
                newPart.inviter_id = UserConfig.getClientUserId();
                newPart.date = ConnectionsManager.getInstance().getCurrentTime();
                info.participants.participants.add(0, newPart);
                MessagesStorage.getInstance().updateChatInfo(info, true);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, false, null);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
            }
        }
    }

    public void deleteUserFromChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatFull info) {
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
                    if (chat.creator) {
                        TLRPC.TL_channels_deleteChannel req = new TLRPC.TL_channels_deleteChannel();
                        req.channel = getInputChannel(chat);
                        request = req;
                    } else {
                        TLRPC.TL_channels_leaveChannel req = new TLRPC.TL_channels_leaveChannel();
                        req.channel = getInputChannel(chat);
                        request = req;
                    }
                } else {
                    TLRPC.TL_channels_kickFromChannel req = new TLRPC.TL_channels_kickFromChannel();
                    req.channel = getInputChannel(chat);
                    req.user_id = inputUser;
                    req.kicked = true;
                    request = req;
                }
            } else {
                TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
                req.chat_id = chat_id;
                req.user_id = getInputUser(user);
                request = req;
            }
            ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (user.id == UserConfig.getClientUserId()) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                deleteDialog(-chat_id, 0);
                            }
                        });
                    }
                    if (error != null) {
                        return;
                    }
                    final TLRPC.Updates updates = (TLRPC.Updates) response;
                    processUpdates(updates, false);
                    if (isChannel && !(inputUser instanceof TLRPC.TL_inputUserSelf)) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                loadFullChat(chat_id, 0, true);
                            }
                        }, 1000);
                    }
                }
            }, ConnectionsManager.RequestFlagInvokeAfter);
        } else {
            if (info instanceof TLRPC.TL_chatFull) {
                TLRPC.Chat chat = getChat(chat_id);
                chat.participants_count--;
                ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<>();
                chatArrayList.add(chat);
                MessagesStorage.getInstance().putUsersAndChats(null, chatArrayList, true, true);

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
                    MessagesStorage.getInstance().updateChatInfo(info, true);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info, 0, false, null);
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
            }
        }
    }

    public void changeChatTitle(int chat_id, String title) {
        if (chat_id > 0) {
            TLObject request;
            if (ChatObject.isChannel(chat_id)) {
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
            ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        return;
                    }
                    processUpdates((TLRPC.Updates) response, false);
                }
            }, ConnectionsManager.RequestFlagInvokeAfter);
        } else {
            TLRPC.Chat chat = getChat(chat_id);
            chat.title = title;
            ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<>();
            chatArrayList.add(chat);
            MessagesStorage.getInstance().putUsersAndChats(null, chatArrayList, true, true);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_NAME);
        }
    }

    public void changeChatAvatar(int chat_id, TLRPC.InputFile uploadedAvatar) {
        TLObject request;
        if (ChatObject.isChannel(chat_id)) {
            TLRPC.TL_channels_editPhoto req = new TLRPC.TL_channels_editPhoto();
            req.channel = getInputChannel(chat_id);
            if (uploadedAvatar != null) {
                req.photo = new TLRPC.TL_inputChatUploadedPhoto();
                req.photo.file = uploadedAvatar;
                req.photo.crop = new TLRPC.TL_inputPhotoCropAuto();
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
                req.photo.crop = new TLRPC.TL_inputPhotoCropAuto();
            } else {
                req.photo = new TLRPC.TL_inputChatPhotoEmpty();
            }
            request = req;
        }
        ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                processUpdates((TLRPC.Updates) response, false);
            }
        }, ConnectionsManager.RequestFlagInvokeAfter);
    }

    public void unregistedPush() {
        if (UserConfig.registeredForPush && UserConfig.pushString.length() == 0) {
            TLRPC.TL_account_unregisterDevice req = new TLRPC.TL_account_unregisterDevice();
            req.token = UserConfig.pushString;
            req.token_type = 2;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
    }

    public void performLogout(boolean byUser) {
        SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE).edit();
        editor.clear().commit();
        editor = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Activity.MODE_PRIVATE).edit();
        editor.putLong("lastGifLoadTime", 0).commit();
        editor = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE).edit();
        editor.remove("gifhint").commit();

        if (byUser) {
            unregistedPush();
            TLRPC.TL_auth_logOut req = new TLRPC.TL_auth_logOut();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    ConnectionsManager.getInstance().cleanUp();
                }
            });
        } else {
            ConnectionsManager.getInstance().cleanUp();
        }
        UserConfig.clearConfig();
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.appDidLogout);
        MessagesStorage.getInstance().cleanUp(false);
        cleanUp();
        ContactsController.getInstance().deleteAllAppAccounts();
    }

    public void generateUpdateMessage() {
        if (UserConfig.lastUpdateVersion == null || UserConfig.lastUpdateVersion.equals(BuildVars.BUILD_VERSION_STRING)) {
            return;
        }
        TLRPC.TL_help_getAppChangelog req = new TLRPC.TL_help_getAppChangelog();
        req.app_version = BuildVars.BUILD_VERSION_STRING;
        try {
            req.lang_code = LocaleController.getLocaleStringIso639();
            req.device_model = Build.MANUFACTURER + Build.MODEL;
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        if (req.lang_code == null || req.lang_code.trim().length() == 0) {
            req.lang_code = "en";
        }
        if (req.device_model == null || req.device_model.trim().length() == 0) {
            req.device_model = "Android unknown";
        }
        if (req.system_version == null || req.system_version.trim().length() == 0) {
            req.system_version = "SDK Unknown";
        }
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    UserConfig.lastUpdateVersion = BuildVars.BUILD_VERSION_STRING;
                    UserConfig.saveConfig(false);
                }
                if (response instanceof TLRPC.TL_help_appChangelog) {
                    TLRPC.TL_updateServiceNotification update = new TLRPC.TL_updateServiceNotification();
                    update.message = ((TLRPC.TL_help_appChangelog) response).text;
                    update.media = new TLRPC.TL_messageMediaEmpty();
                    update.type = "update";
                    update.popup = false;
                    ArrayList<TLRPC.Update> updates = new ArrayList<>();
                    updates.add(update);
                    processUpdateArray(updates, null, null);
                }
            }
        });
    }

    public void registerForPush(final String regid) {
        if (regid == null || regid.length() == 0 || registeringForPush || UserConfig.getClientUserId() == 0) {
            return;
        }
        if (UserConfig.registeredForPush && regid.equals(UserConfig.pushString)) {
            return;
        }
        registeringForPush = true;
        TLRPC.TL_account_registerDevice req = new TLRPC.TL_account_registerDevice();
        req.token_type = 2;
        req.token = regid;
        req.app_sandbox = false;
        try {
            req.lang_code = LocaleController.getLocaleStringIso639();
            if (req.lang_code.length() == 0) {
                req.lang_code = "en";
            }
            req.device_model = Build.MANUFACTURER + Build.MODEL;
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            req.app_version = pInfo.versionName + " (" + pInfo.versionCode + ")";
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            req.lang_code = "en";
            req.device_model = "Android unknown";
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            req.app_version = "App version unknown";
        }

        if (req.lang_code == null || req.lang_code.trim().length() == 0) {
            req.lang_code = "en";
        }
        if (req.device_model == null || req.device_model.trim().length() == 0) {
            req.device_model = "Android unknown";
        }
        if (req.app_version == null || req.app_version.trim().length() == 0) {
            req.app_version = "App version unknown";
        }
        if (req.system_version == null || req.system_version.trim().length() == 0) {
            req.system_version = "SDK Unknown";
        }

        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (response instanceof TLRPC.TL_boolTrue) {
                    FileLog.e("tmessages", "registered for push");
                    UserConfig.registeredForPush = true;
                    UserConfig.pushString = regid;
                    UserConfig.saveConfig(false);
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        registeringForPush = false;
                    }
                });
            }
        });
    }

    public void loadCurrentState() {
        if (updatingState) {
            return;
        }
        updatingState = true;
        TLRPC.TL_updates_getState req = new TLRPC.TL_updates_getState();
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                updatingState = false;
                if (error == null) {
                    TLRPC.TL_updates_state res = (TLRPC.TL_updates_state) response;
                    MessagesStorage.lastDateValue = res.date;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastQtsValue = res.qts;
                    for (int a = 0; a < 3; a++) {
                        processUpdatesQueue(a, 2);
                    }
                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else {
                    if (error.code != 401) {
                        loadCurrentState();
                    }
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
            if (MessagesStorage.lastSeqValue + 1 == seq || MessagesStorage.lastSeqValue == seq) {
                return 0;
            } else if (MessagesStorage.lastSeqValue < seq) {
                return 1;
            } else {
                return 2;
            }
        } else if (type == 1) {
            if (updates.pts <= MessagesStorage.lastPtsValue) {
                return 2;
            } else if (MessagesStorage.lastPtsValue + updates.pts_count == updates.pts) {
                return 0;
            } else {
                return 1;
            }
        } else if (type == 2) {
            if (updates.pts <= MessagesStorage.lastQtsValue) {
                return 2;
            } else if (MessagesStorage.lastQtsValue + updates.updates.size() == updates.pts) {
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
        Integer channelPts = channelsPts.get(channelId);
        if (updatesQueue.isEmpty() || channelPts == null) {
            updatesQueueChannels.remove(channelId);
            return;
        }
        Collections.sort(updatesQueue, new Comparator<TLRPC.Updates>() {
            @Override
            public int compare(TLRPC.Updates updates, TLRPC.Updates updates2) {
                return AndroidUtilities.compare(updates.pts, updates2.pts);
            }
        });
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
                Long updatesStartWaitTime = updatesStartWaitTimeChannels.get(channelId);
                if (updatesStartWaitTime != null && (anyProceed || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500)) {
                    FileLog.e("tmessages", "HOLE IN CHANNEL " + channelId + " UPDATES QUEUE - will wait more time");
                    if (anyProceed) {
                        updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis());
                    }
                    return;
                } else {
                    FileLog.e("tmessages", "HOLE IN CHANNEL " + channelId + " UPDATES QUEUE - getChannelDifference ");
                    updatesStartWaitTimeChannels.remove(channelId);
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
        updatesStartWaitTimeChannels.remove(channelId);
        FileLog.e("tmessages", "UPDATES CHANNEL " + channelId + " QUEUE PROCEED - OK");
    }

    private void processUpdatesQueue(int type, int state) {
        ArrayList<TLRPC.Updates> updatesQueue = null;
        if (type == 0) {
            updatesQueue = updatesQueueSeq;
            Collections.sort(updatesQueue, new Comparator<TLRPC.Updates>() {
                @Override
                public int compare(TLRPC.Updates updates, TLRPC.Updates updates2) {
                    return AndroidUtilities.compare(getUpdateSeq(updates), getUpdateSeq(updates2));
                }
            });
        } else if (type == 1) {
            updatesQueue = updatesQueuePts;
            Collections.sort(updatesQueue, new Comparator<TLRPC.Updates>() {
                @Override
                public int compare(TLRPC.Updates updates, TLRPC.Updates updates2) {
                    return AndroidUtilities.compare(updates.pts, updates2.pts);
                }
            });
        } else if (type == 2) {
            updatesQueue = updatesQueueQts;
            Collections.sort(updatesQueue, new Comparator<TLRPC.Updates>() {
                @Override
                public int compare(TLRPC.Updates updates, TLRPC.Updates updates2) {
                    return AndroidUtilities.compare(updates.pts, updates2.pts);
                }
            });
        }
        if (updatesQueue != null && !updatesQueue.isEmpty()) {
            boolean anyProceed = false;
            if (state == 2) {
                TLRPC.Updates updates = updatesQueue.get(0);
                if (type == 0) {
                    MessagesStorage.lastSeqValue = getUpdateSeq(updates);
                } else if (type == 1) {
                    MessagesStorage.lastPtsValue = updates.pts;
                } else {
                    MessagesStorage.lastQtsValue = updates.pts;
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
                        FileLog.e("tmessages", "HOLE IN UPDATES QUEUE - will wait more time");
                        if (anyProceed) {
                            setUpdatesStartTime(type, System.currentTimeMillis());
                        }
                        return;
                    } else {
                        FileLog.e("tmessages", "HOLE IN UPDATES QUEUE - getDifference");
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
            FileLog.e("tmessages", "UPDATES QUEUE PROCEED - OK");
        }
        setUpdatesStartTime(type, 0);
    }

    public void startShortPoll(final int channelId, final boolean stop) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (stop) {
                    needShortPollChannels.delete(channelId);
                } else {
                    needShortPollChannels.put(channelId, 0);
                    if (shortPollChannels.indexOfKey(channelId) < 0) {
                        getChannelDifference(channelId, 2);
                    }
                }
            }
        });
    }

    public void getChannelDifference(final int channelId) {
        getChannelDifference(channelId, 0);
    }

    public void getChannelDifference(final int channelId, final int newDialogType) {
        Boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId);
        if (gettingDifferenceChannel == null) {
            gettingDifferenceChannel = false;
        }
        if (gettingDifferenceChannel) {
            return;
        }
        int limit = 100;
        Integer channelPts;
        if (newDialogType == 1) {
            channelPts = channelsPts.get(channelId);
            if (channelPts != null) {
                return;
            }
            channelPts = 1;
            limit = 1;
        } else {
            channelPts = channelsPts.get(channelId);
            if (channelPts == null) {
                channelPts = MessagesStorage.getInstance().getChannelPtsSync(channelId);
                if (channelPts != 0) {
                    channelsPts.put(channelId, channelPts);
                }
                if (channelPts == 0 && newDialogType == 2) {
                    return;
                }
            }
            if (channelPts == 0) {
                return;
            }
        }
        gettingDifferenceChannels.put(channelId, true);
        TLRPC.TL_updates_getChannelDifference req = new TLRPC.TL_updates_getChannelDifference();
        req.channel = getInputChannel(channelId);
        req.filter = new TLRPC.TL_channelMessagesFilterEmpty();
        req.pts = channelPts;
        req.limit = limit;
        FileLog.e("tmessages", "start getChannelDifference with pts = " + channelPts + " channelId = " + channelId);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, final TLRPC.TL_error error) {

                if (error == null) {
                    final TLRPC.updates_ChannelDifference res = (TLRPC.updates_ChannelDifference) response;

                    final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();
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

                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(res.users, false);
                            putChats(res.chats, false);
                        }
                    });

                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            if (!msgUpdates.isEmpty()) {
                                final HashMap<Integer, long[]> corrected = new HashMap<>();
                                for (TLRPC.TL_updateMessageID update : msgUpdates) {
                                    long[] ids = MessagesStorage.getInstance().updateMessageStateAndId(update.random_id, null, update.id, 0, false, channelId);
                                    if (ids != null) {
                                        corrected.put(update.id, ids);
                                    }
                                }

                                if (!corrected.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            for (HashMap.Entry<Integer, long[]> entry : corrected.entrySet()) {
                                                Integer newId = entry.getKey();
                                                long[] ids = entry.getValue();
                                                Integer oldId = (int) ids[1];
                                                SendMessagesHelper.getInstance().processSentMessage(oldId);
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newId, null, ids[0]);
                                            }
                                        }
                                    });
                                }
                            }

                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    if (res instanceof TLRPC.TL_updates_channelDifference || res instanceof TLRPC.TL_updates_channelDifferenceEmpty) {
                                        if (!res.new_messages.isEmpty()) {
                                            final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<>();
                                            ImageLoader.saveMessagesThumbs(res.new_messages);

                                            final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                                            for (int a = 0; a < res.new_messages.size(); a++) {
                                                TLRPC.Message message = res.new_messages.get(a);
                                                if (!message.out) {
                                                    message.unread = true;
                                                    if (message.post || channelFinal != null && channelFinal.megagroup) {
                                                        message.media_unread = true;
                                                    }
                                                }
                                                if (message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                                                    message.unread = false;
                                                    message.media_unread = false;
                                                }
                                                if (channelFinal != null && channelFinal.megagroup) {
                                                    message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                                }

                                                long dialog_id = -channelId;
                                                Integer value = dialogs_read_inbox_max.get(dialog_id);
                                                if (value == null) {
                                                    value = MessagesStorage.getInstance().getChannelReadInboxMax(channelId);
                                                }

                                                MessageObject obj = new MessageObject(message, usersDict, createdDialogIds.contains(dialog_id));
                                                if (channelFinal != null && channelFinal.left || value >= obj.getId()) {
                                                    obj.setIsRead();
                                                    obj.setContentIsRead();
                                                }

                                                if (!obj.isOut() && obj.isContentUnread()) {
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

                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : messages.entrySet()) {
                                                        Long key = pair.getKey();
                                                        ArrayList<MessageObject> value = pair.getValue();
                                                        updateInterfaceWithMessages(key, value);
                                                    }
                                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                                }
                                            });
                                            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                                @Override
                                                public void run() {
                                                    if (!pushMessages.isEmpty()) {
                                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                                            @Override
                                                            public void run() {
                                                                NotificationsController.getInstance().processNewMessages(pushMessages, true);
                                                            }
                                                        });
                                                    }
                                                    MessagesStorage.getInstance().putMessages(res.new_messages, true, false, false, MediaController.getInstance().getAutodownloadMask());
                                                }
                                            });
                                        }

                                        if (!res.other_updates.isEmpty()) {
                                            processUpdateArray(res.other_updates, res.users, res.chats);
                                        }
                                        processChannelsUpdatesQueue(channelId, 1);
                                        MessagesStorage.getInstance().saveChannelPts(channelId, res.pts);
                                    } else if (res instanceof TLRPC.TL_updates_channelDifferenceTooLong) {
                                        for (int a = 0; a < res.messages.size(); a++) {
                                            TLRPC.Message message = res.messages.get(a);
                                            message.dialog_id = -channelId;
                                            if (!message.out) {
                                                message.unread = true;
                                                if (message.post || channelFinal != null && channelFinal.megagroup) {
                                                    message.media_unread = true;
                                                }
                                            }
                                            if (message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                                                message.unread = false;
                                                message.media_unread = false;
                                            }
                                            if (channelFinal != null && channelFinal.megagroup) {
                                                message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                            }

                                            if (channelFinal != null && channelFinal.left || res.read_inbox_max_id >= message.id) {
                                                message.unread = false;
                                                message.media_unread = false;
                                            }
                                            if (channelFinal != null && channelFinal.megagroup) {
                                                message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                            }
                                        }
                                        if (channelFinal != null && channelFinal.megagroup) {
                                            res.unread_important_count = Math.max(res.unread_count, res.unread_important_count);
                                            res.top_important_message = Math.max(res.top_important_message, res.top_message);
                                        }
                                        MessagesStorage.getInstance().overwriteChannel(channelId, (TLRPC.TL_updates_channelDifferenceTooLong) res, newDialogType);
                                    }
                                    gettingDifferenceChannels.remove(channelId);
                                    channelsPts.put(channelId, res.pts);

                                    if ((res.flags & 2) != 0) {
                                        shortPollChannels.put(channelId, (int) (System.currentTimeMillis() / 1000) + res.timeout);
                                    }
                                    if (!res.isFinal) {
                                        getChannelDifference(channelId);
                                    }
                                    FileLog.e("tmessages", "received channel difference with pts = " + res.pts + " channelId = " + channelId);
                                    FileLog.e("tmessages", "messages = " + res.new_messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                                }
                            });
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            checkChannelError(error.text, channelId);
                        }
                    });
                    gettingDifferenceChannels.remove(channelId);
                }
            }
        });
    }

    private void checkChannelError(String text, int channelId) {
        switch (text) {
            case "CHANNEL_PRIVATE":
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 0);
                break;
            case "CHANNEL_PUBLIC_GROUP_NA":
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 1);
                break;
            case "USER_BANNED_IN_CHANNEL":
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoCantLoad, channelId, 2);
                break;
        }
    }

    public void getDifference() {
        getDifference(MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue, false);
    }

    public void getDifference(int pts, int date, int qts, boolean slice) {
        registerForPush(UserConfig.pushString);
        if (MessagesStorage.lastPtsValue == 0) {
            loadCurrentState();
            return;
        }
        if (!slice && gettingDifference) {
            return;
        }
        if (!firstGettingTask) {
            getNewDeleteTask(null);
            firstGettingTask = true;
        }
        gettingDifference = true;
        TLRPC.TL_updates_getDifference req = new TLRPC.TL_updates_getDifference();
        req.pts = pts;
        req.date = date;
        req.qts = qts;
        if (req.date == 0) {
            req.date = ConnectionsManager.getInstance().getCurrentTime();
        }
        FileLog.e("tmessages", "start getDifference with date = " + MessagesStorage.lastDateValue + " pts = " + MessagesStorage.lastPtsValue + " seq = " + MessagesStorage.lastSeqValue);
        ConnectionsManager.getInstance().setIsUpdating(true);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    final TLRPC.updates_Difference res = (TLRPC.updates_Difference) response;

                    if (res instanceof TLRPC.TL_updates_differenceSlice) {
                        getDifference(res.intermediate_state.pts, res.intermediate_state.date, res.intermediate_state.qts, true);
                    }

                    final HashMap<Integer, TLRPC.User> usersDict = new HashMap<>();
                    final HashMap<Integer, TLRPC.Chat> chatsDict = new HashMap<>();
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
                            }
                        }
                    }

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(res.users, false);
                            putChats(res.chats, false);
                        }
                    });

                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, false);
                            if (!msgUpdates.isEmpty()) {
                                final HashMap<Integer, long[]> corrected = new HashMap<>();
                                for (TLRPC.TL_updateMessageID update : msgUpdates) {
                                    long[] ids = MessagesStorage.getInstance().updateMessageStateAndId(update.random_id, null, update.id, 0, false, 0);
                                    if (ids != null) {
                                        corrected.put(update.id, ids);
                                    }
                                }

                                if (!corrected.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            for (HashMap.Entry<Integer, long[]> entry : corrected.entrySet()) {
                                                Integer newId = entry.getKey();
                                                long[] ids = entry.getValue();
                                                Integer oldId = (int) ids[1];
                                                SendMessagesHelper.getInstance().processSentMessage(oldId);
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newId, null, ids[0]);
                                            }
                                        }
                                    });
                                }
                            }

                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    if (!res.new_messages.isEmpty() || !res.new_encrypted_messages.isEmpty()) {
                                        final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<>();
                                        for (TLRPC.EncryptedMessage encryptedMessage : res.new_encrypted_messages) {
                                            ArrayList<TLRPC.Message> decryptedMessages = SecretChatHelper.getInstance().decryptMessage(encryptedMessage);
                                            if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                                                for (TLRPC.Message message : decryptedMessages) {
                                                    res.new_messages.add(message);
                                                }
                                            }
                                        }

                                        ImageLoader.saveMessagesThumbs(res.new_messages);

                                        final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                                        for (int a = 0; a < res.new_messages.size(); a++) {
                                            TLRPC.Message message = res.new_messages.get(a);

                                            if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                                                TLRPC.User user = usersDict.get(message.action.user_id);
                                                if (user != null && user.bot) {
                                                    message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                                                }
                                            } else if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                                                message.unread = false;
                                                message.media_unread = false;
                                            }

                                            long uid;
                                            if (message.dialog_id != 0) {
                                                uid = message.dialog_id;
                                            } else {
                                                if (message.to_id.chat_id != 0) {
                                                    uid = -message.to_id.chat_id;
                                                } else {
                                                    if (message.to_id.user_id == UserConfig.getClientUserId()) {
                                                        message.to_id.user_id = message.from_id;
                                                    }
                                                    uid = message.to_id.user_id;
                                                }
                                            }

                                            MessageObject obj = new MessageObject(message, usersDict, chatsDict, createdDialogIds.contains(uid));

                                            if (!obj.isOut() && obj.isUnread()) {
                                                pushMessages.add(obj);
                                            }

                                            ArrayList<MessageObject> arr = messages.get(uid);
                                            if (arr == null) {
                                                arr = new ArrayList<>();
                                                messages.put(uid, arr);
                                            }
                                            arr.add(obj);
                                        }

                                        AndroidUtilities.runOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : messages.entrySet()) {
                                                    Long key = pair.getKey();
                                                    ArrayList<MessageObject> value = pair.getValue();
                                                    updateInterfaceWithMessages(key, value);
                                                }
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                            }
                                        });
                                        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                if (!pushMessages.isEmpty()) {
                                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            NotificationsController.getInstance().processNewMessages(pushMessages, !(res instanceof TLRPC.TL_updates_differenceSlice));
                                                        }
                                                    });
                                                }
                                                MessagesStorage.getInstance().putMessages(res.new_messages, true, false, false, MediaController.getInstance().getAutodownloadMask());
                                            }
                                        });

                                        SecretChatHelper.getInstance().processPendingEncMessages();
                                    }

                                    if (!res.other_updates.isEmpty()) {
                                        processUpdateArray(res.other_updates, res.users, res.chats);
                                    }

                                    if (res instanceof TLRPC.TL_updates_difference) {
                                        gettingDifference = false;
                                        MessagesStorage.lastSeqValue = res.state.seq;
                                        MessagesStorage.lastDateValue = res.state.date;
                                        MessagesStorage.lastPtsValue = res.state.pts;
                                        MessagesStorage.lastQtsValue = res.state.qts;
                                        ConnectionsManager.getInstance().setIsUpdating(false);
                                        for (int a = 0; a < 3; a++) {
                                            processUpdatesQueue(a, 1);
                                        }
                                    } else if (res instanceof TLRPC.TL_updates_differenceSlice) {
                                        MessagesStorage.lastDateValue = res.intermediate_state.date;
                                        MessagesStorage.lastPtsValue = res.intermediate_state.pts;
                                        MessagesStorage.lastQtsValue = res.intermediate_state.qts;
                                    } else if (res instanceof TLRPC.TL_updates_differenceEmpty) {
                                        gettingDifference = false;
                                        MessagesStorage.lastSeqValue = res.seq;
                                        MessagesStorage.lastDateValue = res.date;
                                        ConnectionsManager.getInstance().setIsUpdating(false);
                                        for (int a = 0; a < 3; a++) {
                                            processUpdatesQueue(a, 1);
                                        }
                                    }
                                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                                    FileLog.e("tmessages", "received difference with date = " + MessagesStorage.lastDateValue + " pts = " + MessagesStorage.lastPtsValue + " seq = " + MessagesStorage.lastSeqValue + " messages = " + res.new_messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                                }
                            });
                        }
                    });
                } else {
                    gettingDifference = false;
                    ConnectionsManager.getInstance().setIsUpdating(false);
                }
            }
        });
    }

    public void generateJoinMessage(final int chat_id, boolean ignoreLeft) {
        TLRPC.Chat chat = getChat(chat_id);
        if (chat == null || !ChatObject.isChannel(chat_id) || (chat.left || chat.kicked) && !ignoreLeft) {
            return;
        }

        TLRPC.TL_messageService message = new TLRPC.TL_messageService();
        message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
        message.local_id = message.id = UserConfig.getNewMessageId();
        message.date = ConnectionsManager.getInstance().getCurrentTime();
        message.from_id = UserConfig.getClientUserId();
        message.to_id = new TLRPC.TL_peerChannel();
        message.to_id.channel_id = chat_id;
        message.dialog_id = -chat_id;
        message.post = true;
        message.action = new TLRPC.TL_messageActionChatAddUser();
        message.action.users.add(UserConfig.getClientUserId());
        if (chat.megagroup) {
            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
        }
        UserConfig.saveConfig(false);

        final ArrayList<MessageObject> pushMessages = new ArrayList<>();
        final ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();

        messagesArr.add(message);
        MessageObject obj = new MessageObject(message, null, true);
        pushMessages.add(obj);

        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationsController.getInstance().processNewMessages(pushMessages, true);
                    }
                });
            }
        });
        MessagesStorage.getInstance().putMessages(messagesArr, true, true, false, MediaController.getInstance().getAutodownloadMask());

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                updateInterfaceWithMessages(-chat_id, pushMessages);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        });
    }

    public void convertGroup() {

    }

    public void checkChannelInviter(final int chat_id) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                final TLRPC.Chat chat = getChat(chat_id);
                if (chat == null || !ChatObject.isChannel(chat_id) || chat.creator) {
                    return;
                }
                TLRPC.TL_channels_getParticipant req = new TLRPC.TL_channels_getParticipant();
                req.channel = getInputChannel(chat_id);
                req.user_id = new TLRPC.TL_inputUserSelf();
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        final TLRPC.TL_channels_channelParticipant res = (TLRPC.TL_channels_channelParticipant) response;
                        if (res != null && res.participant instanceof TLRPC.TL_channelParticipantSelf && res.participant.inviter_id != UserConfig.getClientUserId()) {
                            if (chat.megagroup && MessagesStorage.getInstance().isMigratedChat(chat.id)) {
                                return;
                            }
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    putUsers(res.users, false);
                                }
                            });
                            MessagesStorage.getInstance().putUsersAndChats(res.users, null, true, true);

                            TLRPC.TL_messageService message = new TLRPC.TL_messageService();
                            message.media_unread = true;
                            message.unread = true;
                            message.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                            message.post = true;
                            if (chat.megagroup) {
                                message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                            }
                            message.local_id = message.id = UserConfig.getNewMessageId();
                            message.date = res.participant.date;
                            message.action = new TLRPC.TL_messageActionChatAddUser();
                            message.from_id = res.participant.inviter_id;
                            message.action.users.add(UserConfig.getClientUserId());
                            message.to_id = new TLRPC.TL_peerChannel();
                            message.to_id.channel_id = chat_id;
                            message.dialog_id = -chat_id;
                            UserConfig.saveConfig(false);

                            final ArrayList<MessageObject> pushMessages = new ArrayList<>();
                            final ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();

                            ConcurrentHashMap<Integer, TLRPC.User> usersDict = new ConcurrentHashMap<>();
                            for (int a = 0; a < res.users.size(); a++) {
                                TLRPC.User user = res.users.get(a);
                                usersDict.put(user.id, user);
                            }

                            messagesArr.add(message);
                            MessageObject obj = new MessageObject(message, usersDict, true);
                            pushMessages.add(obj);

                            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            NotificationsController.getInstance().processNewMessages(pushMessages, true);
                                        }
                                    });
                                }
                            });
                            MessagesStorage.getInstance().putMessages(messagesArr, true, true, false, MediaController.getInstance().getAutodownloadMask());

                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    updateInterfaceWithMessages(-chat_id, pushMessages);
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                                }
                            });
                        }
                    }
                });
            }
        });
    }

    private int getUpdateType(TLRPC.Update update) {
        if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateReadMessagesContents || update instanceof TLRPC.TL_updateReadHistoryInbox ||
                update instanceof TLRPC.TL_updateReadHistoryOutbox || update instanceof TLRPC.TL_updateDeleteMessages || update instanceof TLRPC.TL_updateWebPage ||
                update instanceof TLRPC.TL_updateEditMessage) {
            return 0;
        } else if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
            return 1;
        } else if (update instanceof TLRPC.TL_updateNewChannelMessage || update instanceof TLRPC.TL_updateDeleteChannelMessages || update instanceof TLRPC.TL_updateEditChannelMessage) {
            return 2;
        } else {
            return 3;
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
            processUpdateArray(arr, null, null);
        } else if (updates instanceof TLRPC.TL_updateShortChatMessage || updates instanceof TLRPC.TL_updateShortMessage) {
            final int user_id = updates instanceof TLRPC.TL_updateShortChatMessage ? updates.from_id : updates.user_id;
            TLRPC.User user = getUser(user_id);
            TLRPC.User user2 = null;
            TLRPC.User user3 = null;
            TLRPC.Chat channel = null;

            if (user == null || user.min) { //TODO
                user = MessagesStorage.getInstance().getUserSync(user_id);
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
                        user2 = MessagesStorage.getInstance().getUserSync(updates.fwd_from.from_id);
                        putUser(user2, true);
                    }
                    needFwdUser = true;
                }
                if (updates.fwd_from.channel_id != 0) {
                    channel = getChat(updates.fwd_from.channel_id);
                    if (channel == null) {
                        channel = MessagesStorage.getInstance().getChatSync(updates.fwd_from.channel_id);
                        putChat(channel, true);
                    }
                    needFwdUser = true;
                }
            }

            boolean needBotUser = false;
            if (updates.via_bot_id != 0) {
                user3 = getUser(updates.via_bot_id);
                if (user3 == null) {
                    user3 = MessagesStorage.getInstance().getUserSync(updates.via_bot_id);
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
                    chat = MessagesStorage.getInstance().getChatSync(updates.chat_id);
                    putChat(chat, true);
                }
                missingData = chat == null || user == null || needFwdUser && user2 == null && channel == null || needBotUser && user3 == null;
            }
            if (user != null && user.status != null && user.status.expires <= 0) {
                onlinePrivacy.put(user.id, ConnectionsManager.getInstance().getCurrentTime());
                updateStatus = true;
            }

            if (missingData) {
                needGetDiff = true;
            } else {
                if (MessagesStorage.lastPtsValue + updates.pts_count == updates.pts) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.id = updates.id;
                    if (updates instanceof TLRPC.TL_updateShortMessage) {
                        if (updates.out) {
                            message.from_id = UserConfig.getClientUserId();
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
                    message.unread = updates.unread;
                    message.mentioned = updates.mentioned;
                    message.media_unread = updates.media_unread;
                    message.entities = updates.entities;
                    message.message = updates.message;
                    message.date = updates.date;
                    message.via_bot_id = updates.via_bot_id;
                    message.flags = updates.flags | TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    message.reply_to_msg_id = updates.reply_to_msg_id;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    MessagesStorage.lastPtsValue = updates.pts;
                    final MessageObject obj = new MessageObject(message, null, createdDialogIds.contains(message.dialog_id));
                    final ArrayList<MessageObject> objArr = new ArrayList<>();
                    objArr.add(obj);
                    ArrayList<TLRPC.Message> arr = new ArrayList<>();
                    arr.add(message);
                    if (updates instanceof TLRPC.TL_updateShortMessage) {
                        final boolean printUpdate = !updates.out && updatePrintingUsersWithNewMessages(updates.user_id, objArr);
                        if (printUpdate) {
                            updatePrintingStrings();
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (printUpdate) {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                                }
                                updateInterfaceWithMessages(user_id, objArr);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            }
                        });
                    } else {
                        final boolean printUpdate = updatePrintingUsersWithNewMessages(-updates.chat_id, objArr);
                        if (printUpdate) {
                            updatePrintingStrings();
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (printUpdate) {
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                                }

                                updateInterfaceWithMessages(-updates.chat_id, objArr);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            }
                        });
                    }

                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!obj.isOut()) {
                                        NotificationsController.getInstance().processNewMessages(objArr, true);
                                    }
                                }
                            });
                        }
                    });
                    MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
                } else if (MessagesStorage.lastPtsValue != updates.pts) {
                    FileLog.e("tmessages", "need get diff short message, pts: " + MessagesStorage.lastPtsValue + " " + updates.pts + " count = " + updates.pts_count);
                    if (gettingDifference || updatesStartWaitTimePts == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
                        if (updatesStartWaitTimePts == 0) {
                            updatesStartWaitTimePts = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add to queue");
                        updatesQueuePts.add(updates);
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesCombined || updates instanceof TLRPC.TL_updates) {
            HashMap<Integer, TLRPC.Chat> minChannels = null;
            for (int a = 0; a < updates.chats.size(); a++) {
                TLRPC.Chat chat = updates.chats.get(a);
                if (chat instanceof TLRPC.TL_channel && chat.min) {
                    TLRPC.Chat existChat = getChat(chat.id);
                    if (existChat == null || existChat.min) {
                        TLRPC.Chat cacheChat = MessagesStorage.getInstance().getChatSync(updates.chat_id);
                        if (existChat == null) {
                            putChat(cacheChat, true);
                        }
                        existChat = cacheChat;
                    }
                    if (existChat == null || existChat.min) {
                        if (minChannels == null) {
                            minChannels = new HashMap<>();
                        }
                        minChannels.put(chat.id, chat);
                    }
                }
            }
            if (minChannels != null) {
                for (int a = 0; a < updates.updates.size(); a++) {
                    TLRPC.Update update = updates.updates.get(a);
                    if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                        int channelId = ((TLRPC.TL_updateNewChannelMessage) update).message.to_id.channel_id;
                        if (minChannels.containsKey(channelId)) {
                            FileLog.e("tmessages", "need get diff because of min channel " + channelId);
                            needGetDiff = true;
                            break;
                        }
                    }
                }
            }
            if (!needGetDiff) {
                MessagesStorage.getInstance().putUsersAndChats(updates.users, updates.chats, true, true);
                Collections.sort(updates.updates, new Comparator<TLRPC.Update>() {
                    @Override
                    public int compare(TLRPC.Update lhs, TLRPC.Update rhs) {
                        int ltype = getUpdateType(lhs);
                        int rtype = getUpdateType(rhs);
                        if (ltype != rtype) {
                            return AndroidUtilities.compare(ltype, rtype);
                        } else if (ltype == 0 || ltype == 2) {
                            return AndroidUtilities.compare(lhs.pts, rhs.pts);
                        } else if (ltype == 1) {
                            return AndroidUtilities.compare(lhs.qts, rhs.qts);
                        }
                        return 0;
                    }
                });
                for (int a = 0; a < updates.updates.size(); a++) {
                    TLRPC.Update update = updates.updates.get(a);
                    if (getUpdateType(update) == 0) {
                        TLRPC.TL_updates updatesNew = new TLRPC.TL_updates();
                        updatesNew.updates.add(update);
                        updatesNew.pts = update.pts;
                        updatesNew.pts_count = update.pts_count;
                        for (int b = a + 1; b < updates.updates.size(); b++) {
                            TLRPC.Update update2 = updates.updates.get(b);
                            if (getUpdateType(update2) == 0 && updatesNew.pts + update2.pts_count == update2.pts) {
                                updatesNew.updates.add(update2);
                                updatesNew.pts = update2.pts;
                                updatesNew.pts_count += update2.pts_count;
                                updates.updates.remove(b);
                                b--;
                            } else {
                                break;
                            }
                        }
                        if (MessagesStorage.lastPtsValue + updatesNew.pts_count == updatesNew.pts) {
                            if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats)) {
                                FileLog.e("tmessages", "need get diff inner TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                                needGetDiff = true;
                            } else {
                                MessagesStorage.lastPtsValue = updatesNew.pts;
                            }
                        } else if (MessagesStorage.lastPtsValue != updatesNew.pts) {
                            FileLog.e("tmessages", update + " need get diff, pts: " + MessagesStorage.lastPtsValue + " " + updatesNew.pts + " count = " + updatesNew.pts_count);
                            if (gettingDifference || updatesStartWaitTimePts == 0 || updatesStartWaitTimePts != 0 && Math.abs(System.currentTimeMillis() - updatesStartWaitTimePts) <= 1500) {
                                if (updatesStartWaitTimePts == 0) {
                                    updatesStartWaitTimePts = System.currentTimeMillis();
                                }
                                FileLog.e("tmessages", "add to queue");
                                updatesQueuePts.add(updatesNew);
                            } else {
                                needGetDiff = true;
                            }
                        }
                    } else if (getUpdateType(update) == 1) {
                        TLRPC.TL_updates updatesNew = new TLRPC.TL_updates();
                        updatesNew.updates.add(update);
                        updatesNew.pts = update.qts;
                        for (int b = a + 1; b < updates.updates.size(); b++) {
                            TLRPC.Update update2 = updates.updates.get(b);
                            if (getUpdateType(update2) == 1 && updatesNew.pts + 1 == update2.qts) {
                                updatesNew.updates.add(update2);
                                updatesNew.pts = update2.qts;
                                updates.updates.remove(b);
                                b--;
                            } else {
                                break;
                            }
                        }
                        if (MessagesStorage.lastQtsValue == 0 || MessagesStorage.lastQtsValue + updatesNew.updates.size() == updatesNew.pts) {
                            processUpdateArray(updatesNew.updates, updates.users, updates.chats);
                            MessagesStorage.lastQtsValue = updatesNew.pts;
                            needReceivedQueue = true;
                        } else if (MessagesStorage.lastPtsValue != updatesNew.pts) {
                            FileLog.e("tmessages", update + " need get diff, qts: " + MessagesStorage.lastQtsValue + " " + updatesNew.pts);
                            if (gettingDifference || updatesStartWaitTimeQts == 0 || updatesStartWaitTimeQts != 0 && Math.abs(System.currentTimeMillis() - updatesStartWaitTimeQts) <= 1500) {
                                if (updatesStartWaitTimeQts == 0) {
                                    updatesStartWaitTimeQts = System.currentTimeMillis();
                                }
                                FileLog.e("tmessages", "add to queue");
                                updatesQueueQts.add(updatesNew);
                            } else {
                                needGetDiff = true;
                            }
                        }
                    } else if (getUpdateType(update) == 2) {
                        int channelId;
                        if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                            channelId = ((TLRPC.TL_updateNewChannelMessage) update).message.to_id.channel_id;
                        } else if (update instanceof TLRPC.TL_updateEditChannelMessage) {
                            channelId = ((TLRPC.TL_updateEditChannelMessage) update).message.to_id.channel_id;
                        } else {
                            channelId = update.channel_id;
                        }
                        Integer channelPts = channelsPts.get(channelId);
                        if (channelPts == null) {
                            channelPts = MessagesStorage.getInstance().getChannelPtsSync(channelId);
                            if (channelPts == 0) {
                                channelPts = update.pts - update.pts_count;
                            }
                            channelsPts.put(channelId, channelPts);
                        }
                        TLRPC.TL_updates updatesNew = new TLRPC.TL_updates();
                        updatesNew.updates.add(update);
                        updatesNew.pts = update.pts;
                        updatesNew.pts_count = update.pts_count;
                        for (int b = a + 1; b < updates.updates.size(); b++) {
                            TLRPC.Update update2 = updates.updates.get(b);
                            if (getUpdateType(update2) == 2 && updatesNew.pts + update2.pts_count == update2.pts) {
                                updatesNew.updates.add(update2);
                                updatesNew.pts = update2.pts;
                                updatesNew.pts_count += update2.pts_count;
                                updates.updates.remove(b);
                                b--;
                            } else {
                                break;
                            }
                        }
                        if (channelPts + updatesNew.pts_count == updatesNew.pts) {
                            if (!processUpdateArray(updatesNew.updates, updates.users, updates.chats)) {
                                FileLog.e("tmessages", "need get channel diff inner TL_updates, channel_id = " + channelId);
                                if (needGetChannelsDiff == null) {
                                    needGetChannelsDiff = new ArrayList<>();
                                } else if (!needGetChannelsDiff.contains(channelId)) {
                                    needGetChannelsDiff.add(channelId);
                                }
                            } else {
                                channelsPts.put(channelId, updatesNew.pts);
                                MessagesStorage.getInstance().saveChannelPts(channelId, updatesNew.pts);
                            }
                        } else if (channelPts != updatesNew.pts) {
                            FileLog.e("tmessages", update + " need get channel diff, pts: " + channelPts + " " + updatesNew.pts + " count = " + updatesNew.pts_count + " channelId = " + channelId);
                            Long updatesStartWaitTime = updatesStartWaitTimeChannels.get(channelId);
                            Boolean gettingDifferenceChannel = gettingDifferenceChannels.get(channelId);
                            if (gettingDifferenceChannel == null) {
                                gettingDifferenceChannel = false;
                            }
                            if (gettingDifferenceChannel || updatesStartWaitTime == null || Math.abs(System.currentTimeMillis() - updatesStartWaitTime) <= 1500) {
                                if (updatesStartWaitTime == null) {
                                    updatesStartWaitTimeChannels.put(channelId, System.currentTimeMillis());
                                }
                                FileLog.e("tmessages", "add to queue");
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
                        break;
                    }
                    updates.updates.remove(a);
                    a--;
                }

                boolean processUpdate;
                if (updates instanceof TLRPC.TL_updatesCombined) {
                    processUpdate = MessagesStorage.lastSeqValue + 1 == updates.seq_start || MessagesStorage.lastSeqValue == updates.seq_start;
                } else {
                    processUpdate = MessagesStorage.lastSeqValue + 1 == updates.seq || updates.seq == 0 || updates.seq == MessagesStorage.lastSeqValue;
                }
                if (processUpdate) {
                    processUpdateArray(updates.updates, updates.users, updates.chats);
                    if (updates.date != 0) {
                        MessagesStorage.lastDateValue = updates.date;
                    }
                    if (updates.seq != 0) {
                        MessagesStorage.lastSeqValue = updates.seq;
                    }
                } else {
                    if (updates instanceof TLRPC.TL_updatesCombined) {
                        FileLog.e("tmessages", "need get diff TL_updatesCombined, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq_start);
                    } else {
                        FileLog.e("tmessages", "need get diff TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                    }

                    if (gettingDifference || updatesStartWaitTimeSeq == 0 || Math.abs(System.currentTimeMillis() - updatesStartWaitTimeSeq) <= 1500) {
                        if (updatesStartWaitTimeSeq == 0) {
                            updatesStartWaitTimeSeq = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_updates/Combined to queue");
                        updatesQueueSeq.add(updates);
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesTooLong) {
            FileLog.e("tmessages", "need get diff TL_updatesTooLong");
            needGetDiff = true;
        } else if (updates instanceof UserActionUpdatesSeq) {
            MessagesStorage.lastSeqValue = updates.seq;
        } else if (updates instanceof UserActionUpdatesPts) {
            if (updates.chat_id != 0) {
                channelsPts.put(updates.chat_id, updates.pts);
                MessagesStorage.getInstance().saveChannelPts(updates.chat_id, updates.pts);
            } else {
                MessagesStorage.lastPtsValue = updates.pts;
            }
        }
        SecretChatHelper.getInstance().processPendingEncMessages();
        if (!fromQueue) {
            ArrayList<Integer> keys = new ArrayList<>(updatesQueueChannels.keySet());
            for (int a = 0; a < keys.size(); a++) {
                Integer key = keys.get(a);
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
            req.max_qts = MessagesStorage.lastQtsValue;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
        if (updateStatus) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_STATUS);
                }
            });
        }
        MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
    }

    public boolean processUpdateArray(ArrayList<TLRPC.Update> updates, final ArrayList<TLRPC.User> usersArr, final ArrayList<TLRPC.Chat> chatsArr) {
        if (updates.isEmpty()) {
            if (usersArr != null || chatsArr != null) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        putUsers(usersArr, false);
                        putChats(chatsArr, false);
                    }
                });
            }
            return true;
        }
        long currentTime = System.currentTimeMillis();

        final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<>();
        final HashMap<Long, TLRPC.WebPage> webPages = new HashMap<>();
        final ArrayList<MessageObject> pushMessages = new ArrayList<>();
        final ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();
        final HashMap<Long, ArrayList<MessageObject>> editingMessages = new HashMap<>();
        final SparseArray<SparseIntArray> channelViews = new SparseArray<>();
        final SparseArray<ArrayList<TLRPC.TL_messageGroup>> channelsGroups = new SparseArray<>();
        final SparseArray<Long> markAsReadMessagesInbox = new SparseArray<>();
        final SparseIntArray markAsReadMessagesOutbox = new SparseIntArray();
        final ArrayList<Long> markAsReadMessages = new ArrayList<>();
        final HashMap<Integer, Integer> markAsReadEncrypted = new HashMap<>();
        final SparseArray<ArrayList<Integer>> deletedMessages = new SparseArray<>();
        boolean printChanged = false;
        final ArrayList<TLRPC.ChatParticipants> chatInfoToUpdate = new ArrayList<>();
        final ArrayList<TLRPC.Update> updatesOnMainThread = new ArrayList<>();
        final ArrayList<TLRPC.TL_updateEncryptedMessagesRead> tasks = new ArrayList<>();
        final ArrayList<Integer> contactsIds = new ArrayList<>();

        boolean checkForUsers = true;
        ConcurrentHashMap<Integer, TLRPC.User> usersDict;
        ConcurrentHashMap<Integer, TLRPC.Chat> chatsDict;
        if (usersArr != null) {
            usersDict = new ConcurrentHashMap<>();
            for (int a = 0; a < usersArr.size(); a++) {
                TLRPC.User user = usersArr.get(a);
                usersDict.put(user.id, user);
            }
        } else {
            checkForUsers = false;
            usersDict = users;
        }
        if (chatsArr != null) {
            chatsDict = new ConcurrentHashMap<>();
            for (int a = 0; a < chatsArr.size(); a++) {
                TLRPC.Chat chat = chatsArr.get(a);
                chatsDict.put(chat.id, chat);
            }
        } else {
            checkForUsers = false;
            chatsDict = chats;
        }

        if (usersArr != null || chatsArr != null) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    putUsers(usersArr, false);
                    putChats(chatsArr, false);
                }
            });
        }

        int interfaceUpdateMask = 0;

        for (int c = 0; c < updates.size(); c++) {
            TLRPC.Update update = updates.get(c);
            if (update instanceof TLRPC.TL_updateNewMessage || update instanceof TLRPC.TL_updateNewChannelMessage) {
                TLRPC.Message message;
                if (update instanceof TLRPC.TL_updateNewMessage) {
                    message = ((TLRPC.TL_updateNewMessage) update).message;
                } else {
                    message = ((TLRPC.TL_updateNewChannelMessage) update).message;
                    if (!message.out && message.from_id == UserConfig.getClientUserId()) { //TODO remove later
                        message.out = true;
                    }
                }
                TLRPC.Chat chat = null;
                if (checkForUsers) {
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
                            chat = MessagesStorage.getInstance().getChatSync(chat_id);
                            putChat(chat, true);
                        }
                        if (chat == null) {
                            return false;
                        }
                        if (chat.megagroup) {
                            message.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                        }
                    }

                    for (int a = 0; a < 3; a++) {
                        if (a != 0) {
                            user_id = a == 1 ? message.from_id : (message.fwd_from != null ? message.fwd_from.from_id : 0);
                        }
                        if (user_id > 0) {
                            TLRPC.User user = usersDict.get(user_id);
                            if (user == null || user.min) {
                                user = getUser(user_id);
                            }
                            if (user == null || user.min) {
                                user = MessagesStorage.getInstance().getUserSync(user_id);
                                if (user != null && user.min) {
                                    user = null;
                                }
                                putUser(user, true);
                            }
                            if (user == null) {
                                return false;
                            }
                            if (a == 1 && user.status != null && user.status.expires <= 0) {
                                onlinePrivacy.put(user_id, ConnectionsManager.getInstance().getCurrentTime());
                                interfaceUpdateMask |= UPDATE_MASK_STATUS;
                            }
                        }
                    }
                }
                if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    TLRPC.User user = usersDict.get(message.action.user_id);
                    if (user != null && user.bot) {
                        message.reply_markup = new TLRPC.TL_replyKeyboardHide();
                    } else if (message.from_id == UserConfig.getClientUserId() && message.action.user_id == UserConfig.getClientUserId()) {
                        continue;
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatMigrateTo || message.action instanceof TLRPC.TL_messageActionChannelCreate) {
                    message.unread = false;
                    message.media_unread = false;
                }
                if (update instanceof TLRPC.TL_updateNewChannelMessage) {
                    if (message.to_id.channel_id != 0 && !message.out) {
                        message.unread = true;
                        if (message.post || (message.flags & TLRPC.MESSAGE_FLAG_MEGAGROUP) != 0) {
                            message.media_unread = true;
                        }
                    }

                    long dialog_id = -update.channel_id;
                    Integer value = dialogs_read_inbox_max.get(dialog_id);
                    if (value == null) {
                        value = MessagesStorage.getInstance().getChannelReadInboxMax(update.channel_id);
                    }
                    if (value >= message.id || ChatObject.isNotInChat(chat)) {
                        message.unread = false;
                        message.media_unread = false;
                    }
                }
                messagesArr.add(message);
                ImageLoader.saveMessageThumbs(message);
                if (message.to_id.chat_id != 0) {
                    message.dialog_id = -message.to_id.chat_id;
                } else if (message.to_id.channel_id != 0) {
                    message.dialog_id = -message.to_id.channel_id;
                } else {
                    if (message.to_id.user_id == UserConfig.getClientUserId()) {
                        message.to_id.user_id = message.from_id;
                    }
                    message.dialog_id = message.to_id.user_id;
                }
                MessageObject obj = new MessageObject(message, usersDict, chatsDict, createdDialogIds.contains(message.dialog_id));
                if (obj.type == 11) {
                    interfaceUpdateMask |= UPDATE_MASK_CHAT_AVATAR;
                } else if (obj.type == 10) {
                    interfaceUpdateMask |= UPDATE_MASK_CHAT_NAME;
                }
                ArrayList<MessageObject> arr = messages.get(message.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<>();
                    messages.put(message.dialog_id, arr);
                }
                arr.add(obj);
                if (!obj.isOut() && (obj.isUnread() && message.to_id.channel_id == 0 || obj.isContentUnread())) {
                    pushMessages.add(obj);
                }
            } else if (update instanceof TLRPC.TL_updateReadMessagesContents) {
                for (int a = 0; a < update.messages.size(); a++) {
                    long id = update.messages.get(a);
                    markAsReadMessages.add(id);
                }
            } else if (update instanceof TLRPC.TL_updateReadHistoryInbox) {
                TLRPC.Peer peer = ((TLRPC.TL_updateReadHistoryInbox) update).peer;
                if (peer.chat_id != 0) {
                    markAsReadMessagesInbox.put(-peer.chat_id, (long) update.max_id);
                } else {
                    markAsReadMessagesInbox.put(peer.user_id, (long) update.max_id);
                }
            } else if (update instanceof TLRPC.TL_updateReadHistoryOutbox) {
                TLRPC.Peer peer = ((TLRPC.TL_updateReadHistoryOutbox) update).peer;
                if (peer.chat_id != 0) {
                    markAsReadMessagesOutbox.put(-peer.chat_id, update.max_id);
                } else {
                    markAsReadMessagesOutbox.put(peer.user_id, update.max_id);
                }
            } else if (update instanceof TLRPC.TL_updateDeleteMessages) {
                ArrayList<Integer> arrayList = deletedMessages.get(0);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(0, arrayList);
                }
                arrayList.addAll(update.messages);
            } else if (update instanceof TLRPC.TL_updateUserTyping || update instanceof TLRPC.TL_updateChatUserTyping) {
                if (update.user_id != UserConfig.getClientUserId()) {
                    long uid = -update.chat_id;
                    if (uid == 0) {
                        uid = update.user_id;
                    }
                    ArrayList<PrintingUser> arr = printingUsers.get(uid);
                    if (update.action instanceof TLRPC.TL_sendMessageCancelAction) {
                        if (arr != null) {
                            for (int a = 0; a < arr.size(); a++) {
                                PrintingUser pu = arr.get(a);
                                if (pu.userId == update.user_id) {
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
                            if (u.userId == update.user_id) {
                                exist = true;
                                u.lastTime = currentTime;
                                if (u.action.getClass() != update.action.getClass()) {
                                    printChanged = true;
                                }
                                u.action = update.action;
                                break;
                            }
                        }
                        if (!exist) {
                            PrintingUser newUser = new PrintingUser();
                            newUser.userId = update.user_id;
                            newUser.lastTime = currentTime;
                            newUser.action = update.action;
                            arr.add(newUser);
                            printChanged = true;
                        }
                    }
                    onlinePrivacy.put(update.user_id, ConnectionsManager.getInstance().getCurrentTime());
                }
            } else if (update instanceof TLRPC.TL_updateChatParticipants) {
                interfaceUpdateMask |= UPDATE_MASK_CHAT_MEMBERS;
                chatInfoToUpdate.add(update.participants);
            } else if (update instanceof TLRPC.TL_updateUserStatus) {
                interfaceUpdateMask |= UPDATE_MASK_STATUS;
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateUserName) {
                interfaceUpdateMask |= UPDATE_MASK_NAME;
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateUserPhoto) {
                interfaceUpdateMask |= UPDATE_MASK_AVATAR;
                MessagesStorage.getInstance().clearUserPhotos(update.user_id);
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateUserPhone) {
                interfaceUpdateMask |= UPDATE_MASK_PHONE;
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateContactRegistered) {
                if (enableJoined && usersDict.containsKey(update.user_id) && !MessagesStorage.getInstance().isDialogHasMessages(update.user_id)) {
                    TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                    newMessage.action = new TLRPC.TL_messageActionUserJoined();
                    newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                    UserConfig.saveConfig(false);
                    newMessage.unread = false;
                    newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                    newMessage.date = update.date;
                    newMessage.from_id = update.user_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.getClientUserId();
                    newMessage.dialog_id = update.user_id;

                    messagesArr.add(newMessage);
                    MessageObject obj = new MessageObject(newMessage, usersDict, chatsDict, createdDialogIds.contains(newMessage.dialog_id));
                    ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        messages.put(newMessage.dialog_id, arr);
                    }
                    arr.add(obj);
                }
            } else if (update instanceof TLRPC.TL_updateContactLink) {
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
            } else if (update instanceof TLRPC.TL_updateNewAuthorization) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.newSessionReceived);
                    }
                });
                TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                newMessage.action = new TLRPC.TL_messageActionLoginUnknownLocation();
                newMessage.action.title = update.device;
                newMessage.action.address = update.location;
                newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                UserConfig.saveConfig(false);
                newMessage.unread = true;
                newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                newMessage.date = update.date;
                newMessage.from_id = 777000;
                newMessage.to_id = new TLRPC.TL_peerUser();
                newMessage.to_id.user_id = UserConfig.getClientUserId();
                newMessage.dialog_id = 777000;

                messagesArr.add(newMessage);
                MessageObject obj = new MessageObject(newMessage, usersDict, chatsDict, createdDialogIds.contains(newMessage.dialog_id));
                ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<>();
                    messages.put(newMessage.dialog_id, arr);
                }
                arr.add(obj);
                pushMessages.add(obj);
            } else if (update instanceof TLRPC.TL_updateNewGeoChatMessage) {
                //DEPRECATED
            } else if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
                ArrayList<TLRPC.Message> decryptedMessages = SecretChatHelper.getInstance().decryptMessage(((TLRPC.TL_updateNewEncryptedMessage) update).message);
                if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                    int cid = ((TLRPC.TL_updateNewEncryptedMessage) update).message.chat_id;
                    long uid = ((long) cid) << 32;
                    ArrayList<MessageObject> arr = messages.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        messages.put(uid, arr);
                    }
                    for (int a = 0; a < decryptedMessages.size(); a++) {
                        TLRPC.Message message = decryptedMessages.get(a);
                        ImageLoader.saveMessageThumbs(message);
                        messagesArr.add(message);
                        MessageObject obj = new MessageObject(message, usersDict, chatsDict, createdDialogIds.contains(uid));
                        arr.add(obj);
                        pushMessages.add(obj);
                    }
                }
            } else if (update instanceof TLRPC.TL_updateEncryptedChatTyping) {
                TLRPC.EncryptedChat encryptedChat = getEncryptedChatDB(update.chat_id);
                if (encryptedChat != null) {
                    update.user_id = encryptedChat.user_id;
                    long uid = ((long) update.chat_id) << 32;
                    ArrayList<PrintingUser> arr = printingUsers.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<>();
                        printingUsers.put(uid, arr);
                    }
                    boolean exist = false;
                    for (PrintingUser u : arr) {
                        if (u.userId == update.user_id) {
                            exist = true;
                            u.lastTime = currentTime;
                            u.action = new TLRPC.TL_sendMessageTypingAction();
                            break;
                        }
                    }
                    if (!exist) {
                        PrintingUser newUser = new PrintingUser();
                        newUser.userId = update.user_id;
                        newUser.lastTime = currentTime;
                        newUser.action = new TLRPC.TL_sendMessageTypingAction();
                        arr.add(newUser);
                        printChanged = true;
                    }
                    onlinePrivacy.put(update.user_id, ConnectionsManager.getInstance().getCurrentTime());
                }
            } else if (update instanceof TLRPC.TL_updateEncryptedMessagesRead) {
                markAsReadEncrypted.put(update.chat_id, Math.max(update.max_date, update.date));
                tasks.add((TLRPC.TL_updateEncryptedMessagesRead) update);
            } else if (update instanceof TLRPC.TL_updateChatParticipantAdd) {
                MessagesStorage.getInstance().updateChatInfo(update.chat_id, update.user_id, 0, update.inviter_id, update.version);
            } else if (update instanceof TLRPC.TL_updateChatParticipantDelete) {
                MessagesStorage.getInstance().updateChatInfo(update.chat_id, update.user_id, 1, 0, update.version);
            } else if (update instanceof TLRPC.TL_updateDcOptions) {
                ConnectionsManager.getInstance().updateDcSettings();
            } else if (update instanceof TLRPC.TL_updateEncryption) {
                SecretChatHelper.getInstance().processUpdateEncryption((TLRPC.TL_updateEncryption) update, usersDict);
            } else if (update instanceof TLRPC.TL_updateUserBlocked) {
                final TLRPC.TL_updateUserBlocked finalUpdate = (TLRPC.TL_updateUserBlocked) update;
                if (finalUpdate.blocked) {
                    ArrayList<Integer> ids = new ArrayList<>();
                    ids.add(finalUpdate.user_id);
                    MessagesStorage.getInstance().putBlockedUsers(ids, false);
                } else {
                    MessagesStorage.getInstance().deleteBlockedUser(finalUpdate.user_id);
                }
                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (finalUpdate.blocked) {
                                    if (!blockedUsers.contains(finalUpdate.user_id)) {
                                        blockedUsers.add(finalUpdate.user_id);
                                    }
                                } else {
                                    blockedUsers.remove((Integer) finalUpdate.user_id);
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.blockedUsersDidLoaded);
                            }
                        });
                    }
                });
            } else if (update instanceof TLRPC.TL_updateNotifySettings) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateServiceNotification) {
                TLRPC.TL_updateServiceNotification notification = (TLRPC.TL_updateServiceNotification) update;
                if (notification.popup && notification.message != null && notification.message.length() > 0) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.needShowAlert, 2, notification.message);
                }
                TLRPC.TL_message newMessage = new TLRPC.TL_message();
                newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                UserConfig.saveConfig(false);
                newMessage.unread = true;
                newMessage.flags = TLRPC.MESSAGE_FLAG_HAS_FROM_ID;
                newMessage.date = ConnectionsManager.getInstance().getCurrentTime();
                newMessage.from_id = 777000;
                newMessage.to_id = new TLRPC.TL_peerUser();
                newMessage.to_id.user_id = UserConfig.getClientUserId();
                newMessage.dialog_id = 777000;
                newMessage.media = update.media;
                newMessage.flags |= TLRPC.MESSAGE_FLAG_HAS_MEDIA;
                newMessage.message = notification.message;

                messagesArr.add(newMessage);
                MessageObject obj = new MessageObject(newMessage, usersDict, chatsDict, createdDialogIds.contains(newMessage.dialog_id));
                ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<>();
                    messages.put(newMessage.dialog_id, arr);
                }
                arr.add(obj);
                pushMessages.add(obj);
            } else if (update instanceof TLRPC.TL_updatePrivacy) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateWebPage) {
                webPages.put(update.webpage.id, update.webpage);
            } else if (update instanceof TLRPC.TL_updateChannelTooLong) {
                if ((update.flags & 1) != 0) {
                    Integer channelPts = channelsPts.get(update.channel_id);
                    if (channelPts == null) {
                        channelPts = MessagesStorage.getInstance().getChannelPtsSync(update.channel_id);
                        if (channelPts == 0) {
                            channelPts = 1;
                        }
                        channelsPts.put(update.channel_id, channelPts);
                    }
                    if (update.pts > channelPts) {
                        getChannelDifference(update.channel_id);
                    }
                } else {
                    getChannelDifference(update.channel_id);
                }
            } else if (update instanceof TLRPC.TL_updateChannelGroup) {
                ArrayList<TLRPC.TL_messageGroup> arrayList = channelsGroups.get(update.channel_id);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    channelsGroups.put(update.channel_id, arrayList);
                }
                arrayList.add(update.group);
            } else if (update instanceof TLRPC.TL_updateReadChannelInbox) {
                long message_id = update.max_id;
                message_id |= ((long) update.channel_id) << 32;
                markAsReadMessagesInbox.put(-update.channel_id, message_id);

                long dialog_id = -update.channel_id;
                Integer value = dialogs_read_inbox_max.get(dialog_id);
                if (value == null) {
                    value = MessagesStorage.getInstance().getChannelReadInboxMax(update.channel_id);
                }
                dialogs_read_inbox_max.put(dialog_id, Math.max(value, update.max_id));
            } else if (update instanceof TLRPC.TL_updateDeleteChannelMessages) {
                ArrayList<Integer> arrayList = deletedMessages.get(update.channel_id);
                if (arrayList == null) {
                    arrayList = new ArrayList<>();
                    deletedMessages.put(update.channel_id, arrayList);
                }
                arrayList.addAll(update.messages);
            } else if (update instanceof TLRPC.TL_updateChannel) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateChannelMessageViews) {
                TLRPC.TL_updateChannelMessageViews updateChannelMessageViews = (TLRPC.TL_updateChannelMessageViews) update;
                SparseIntArray array = channelViews.get(update.channel_id);
                if (array == null) {
                    array = new SparseIntArray();
                    channelViews.put(update.channel_id, array);
                }
                array.put(updateChannelMessageViews.id, update.views);
            } else if (update instanceof TLRPC.TL_updateChatParticipantAdmin) {
                MessagesStorage.getInstance().updateChatInfo(update.chat_id, update.user_id, 2, update.is_admin ? 1 : 0, update.version);
            } else if (update instanceof TLRPC.TL_updateChatAdmins) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateStickerSets) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateStickerSetsOrder) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateNewStickerSet) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateSavedGifs) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateEditChannelMessage || update instanceof TLRPC.TL_updateEditMessage) {
                TLRPC.Message message;
                if (update instanceof TLRPC.TL_updateEditChannelMessage) {
                    message = ((TLRPC.TL_updateEditChannelMessage) update).message;
                } else {
                    message = ((TLRPC.TL_updateEditMessage) update).message;
                }
                if (message.to_id.channel_id != 0 && !message.out) {
                    message.unread = true;
                    if (message.post || (message.flags & TLRPC.MESSAGE_FLAG_MEGAGROUP) != 0) {
                        message.media_unread = true;
                    }
                }

                if (update instanceof TLRPC.TL_updateEditChannelMessage) {
                    long dialog_id = -update.channel_id;
                    Integer value = dialogs_read_inbox_max.get(dialog_id);
                    if (value == null) {
                        value = MessagesStorage.getInstance().getChannelReadInboxMax(update.channel_id);
                    }
                    if (value >= message.id) {
                        message.unread = false;
                        message.media_unread = false;
                    } //TODO unread for updateEditMessage?
                }

                ImageLoader.saveMessageThumbs(message);

                if (message.to_id.chat_id != 0) {
                    message.dialog_id = -message.to_id.chat_id;
                } else if (message.to_id.channel_id != 0) {
                    message.dialog_id = -message.to_id.channel_id;
                } else {
                    if (message.to_id.user_id == UserConfig.getClientUserId()) {
                        message.to_id.user_id = message.from_id;
                    }
                    message.dialog_id = message.to_id.user_id;
                }
                MessageObject obj = new MessageObject(message, usersDict, chatsDict, createdDialogIds.contains(message.dialog_id));

                ArrayList<MessageObject> arr = editingMessages.get(message.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<>();
                    editingMessages.put(message.dialog_id, arr);
                }
                arr.add(obj);
            } else if (update instanceof TLRPC.TL_updateChannelPinnedMessage) {
                TLRPC.TL_updateChannelPinnedMessage updateChannelPinnedMessage = (TLRPC.TL_updateChannelPinnedMessage) update;
                MessagesStorage.getInstance().updateChannelPinnedMessage(update.channel_id, updateChannelPinnedMessage.id);
            }
        }
        if (!messages.isEmpty()) {
            for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : messages.entrySet()) {
                Long key = pair.getKey();
                ArrayList<MessageObject> value = pair.getValue();
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

        if (!contactsIds.isEmpty()) {
            ContactsController.getInstance().processContactsUpdates(contactsIds, usersDict);
        }

        if (!pushMessages.isEmpty()) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationsController.getInstance().processNewMessages(pushMessages, true);
                        }
                    });
                }
            });
        }

        if (!messagesArr.isEmpty()) {
            MessagesStorage.getInstance().putMessages(messagesArr, true, true, false, MediaController.getInstance().getAutodownloadMask());
        }
        if (!editingMessages.isEmpty()) {
            for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : editingMessages.entrySet()) {
                TLRPC.TL_messages_messages messagesRes = new TLRPC.TL_messages_messages();
                ArrayList<MessageObject> messageObjects = pair.getValue();
                for (int a = 0; a < messageObjects.size(); a++) {
                    messagesRes.messages.add(messageObjects.get(a).messageOwner);
                }
                MessagesStorage.getInstance().putMessages(messagesRes, pair.getKey(), -2, 0, 0, false);
            }
        }

        if (channelViews.size() != 0) {
            MessagesStorage.getInstance().putChannelViews(channelViews, true);
        }
        if (channelsGroups.size() != 0) {
            //MessagesStorage.getInstance().applyNewChannelsGroups(channelsGroups);
        }

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                int updateMask = interfaceUpdateMaskFinal;

                if (!updatesOnMainThread.isEmpty()) {
                    ArrayList<TLRPC.User> dbUsers = new ArrayList<>();
                    ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<>();
                    SharedPreferences.Editor editor = null;
                    for (int a = 0; a < updatesOnMainThread.size(); a++) {
                        final TLRPC.Update update = updatesOnMainThread.get(a);
                        final TLRPC.User toDbUser = new TLRPC.User();
                        toDbUser.id = update.user_id;
                        final TLRPC.User currentUser = getUser(update.user_id);
                        if (update instanceof TLRPC.TL_updatePrivacy) {
                            if (update.key instanceof TLRPC.TL_privacyKeyStatusTimestamp) {
                                ContactsController.getInstance().setPrivacyRules(update.rules, false);
                            } else if (update.key instanceof TLRPC.TL_privacyKeyChatInvite) {
                                ContactsController.getInstance().setPrivacyRules(update.rules, true);
                            }
                        } else if (update instanceof TLRPC.TL_updateUserStatus) {
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
                            toDbUser.status = update.status;
                            dbUsersStatus.add(toDbUser);
                            if (update.user_id == UserConfig.getClientUserId()) {
                                NotificationsController.getInstance().setLastOnlineFromOtherDevice(update.status.expires);
                            }
                        } else if (update instanceof TLRPC.TL_updateUserName) {
                            if (currentUser != null) {
                                if (!UserObject.isContact(currentUser)) {
                                    currentUser.first_name = update.first_name;
                                    currentUser.last_name = update.last_name;
                                }
                                if (currentUser.username != null && currentUser.username.length() > 0) {
                                    usersByUsernames.remove(currentUser.username);
                                }
                                if (update.username != null && update.username.length() > 0) {
                                    usersByUsernames.put(update.username, currentUser);
                                }
                                currentUser.username = update.username;
                            }
                            toDbUser.first_name = update.first_name;
                            toDbUser.last_name = update.last_name;
                            toDbUser.username = update.username;
                            dbUsers.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateUserPhoto) {
                            if (currentUser != null) {
                                currentUser.photo = update.photo;
                            }
                            toDbUser.photo = update.photo;
                            dbUsers.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateUserPhone) {
                            if (currentUser != null) {
                                currentUser.phone = update.phone;
                                Utilities.phoneBookQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        ContactsController.getInstance().addContactToPhoneBook(currentUser, true);
                                    }
                                });
                            }
                            toDbUser.phone = update.phone;
                            dbUsers.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateNotifySettings) {
                            TLRPC.TL_updateNotifySettings updateNotifySettings = (TLRPC.TL_updateNotifySettings) update;
                            if (update.notify_settings instanceof TLRPC.TL_peerNotifySettings && updateNotifySettings.peer instanceof TLRPC.TL_notifyPeer) {
                                if (editor == null) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    editor = preferences.edit();
                                }
                                long dialog_id;
                                if (updateNotifySettings.peer.peer.user_id != 0) {
                                    dialog_id = updateNotifySettings.peer.peer.user_id;
                                } else if (updateNotifySettings.peer.peer.chat_id != 0) {
                                    dialog_id = -updateNotifySettings.peer.peer.chat_id;
                                } else {
                                    dialog_id = -updateNotifySettings.peer.peer.channel_id;
                                }

                                TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
                                if (dialog != null) {
                                    dialog.notify_settings = update.notify_settings;
                                }
                                editor.putBoolean("silent_" + dialog_id, update.notify_settings.silent);
                                if (update.notify_settings.mute_until > ConnectionsManager.getInstance().getCurrentTime()) {
                                    int until = 0;
                                    if (update.notify_settings.mute_until > ConnectionsManager.getInstance().getCurrentTime() + 60 * 60 * 24 * 365) {
                                        editor.putInt("notify2_" + dialog_id, 2);
                                        if (dialog != null) {
                                            dialog.notify_settings.mute_until = Integer.MAX_VALUE;
                                        }
                                    } else {
                                        until = update.notify_settings.mute_until;
                                        editor.putInt("notify2_" + dialog_id, 3);
                                        editor.putInt("notifyuntil_" + dialog_id, update.notify_settings.mute_until);
                                        if (dialog != null) {
                                            dialog.notify_settings.mute_until = until;
                                        }
                                    }
                                    MessagesStorage.getInstance().setDialogFlags(dialog_id, ((long) until << 32) | 1);
                                    NotificationsController.getInstance().removeNotificationsForDialog(dialog_id);
                                } else {
                                    if (dialog != null) {
                                        dialog.notify_settings.mute_until = 0;
                                    }
                                    editor.remove("notify2_" + dialog_id);
                                    MessagesStorage.getInstance().setDialogFlags(dialog_id, 0);
                                }
                            }
                        } else if (update instanceof TLRPC.TL_updateChannel) {
                            TLRPC.Dialog dialog = dialogs_dict.get(-(long) update.channel_id);
                            TLRPC.Chat chat = getChat(update.channel_id);
                            if (dialog == null && chat instanceof TLRPC.TL_channel && !chat.left) {
                                Utilities.stageQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        getChannelDifference(update.channel_id, 1);
                                    }
                                });
                            } else if (chat.left && dialog != null) {
                                deleteDialog(dialog.id, 0);
                            }
                            updateMask |= UPDATE_MASK_CHANNEL;
                            loadFullChat(update.channel_id, 0, true);
                        } else if (update instanceof TLRPC.TL_updateChatAdmins) {
                            updateMask |= UPDATE_MASK_CHAT_ADMINS;
                        } else if (update instanceof TLRPC.TL_updateStickerSets) {
                            StickersQuery.loadStickers(false, true);
                        } else if (update instanceof TLRPC.TL_updateStickerSetsOrder) {
                            StickersQuery.reorderStickers(update.order);
                        } else if (update instanceof TLRPC.TL_updateNewStickerSet) {
                            StickersQuery.addNewStickerSet(update.stickerset);
                        } else if (update instanceof TLRPC.TL_updateSavedGifs) {
                            SharedPreferences.Editor editor2 = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Activity.MODE_PRIVATE).edit();
                            editor2.putLong("lastGifLoadTime", 0).commit();
                        }
                    }
                    if (editor != null) {
                        editor.commit();
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    }
                    MessagesStorage.getInstance().updateUsers(dbUsersStatus, true, true, true);
                    MessagesStorage.getInstance().updateUsers(dbUsers, false, true, true);
                }

                if (!webPages.isEmpty()) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didReceivedWebpagesInUpdates, webPages);
                    for (HashMap.Entry<Long, TLRPC.WebPage> entry : webPages.entrySet()) {
                        ArrayList<MessageObject> arrayList = reloadingWebpagesPending.remove(entry.getKey());
                        if (arrayList != null) {
                            TLRPC.WebPage webpage = entry.getValue();
                            ArrayList<TLRPC.Message> messagesArr = new ArrayList<>();
                            long dialog_id = 0;
                            if (webpage instanceof TLRPC.TL_webPage || webpage instanceof TLRPC.TL_webPageEmpty) {
                                for (int a = 0; a < arrayList.size(); a++) {
                                    arrayList.get(a).messageOwner.media.webpage = webpage;
                                    if (a == 0) {
                                        dialog_id = arrayList.get(a).getDialogId();
                                        ImageLoader.saveMessageThumbs(arrayList.get(a).messageOwner);
                                    }
                                    messagesArr.add(arrayList.get(a).messageOwner);
                                }
                            } else {
                                reloadingWebpagesPending.put(webpage.id, arrayList);
                            }
                            if (!messagesArr.isEmpty()) {
                                MessagesStorage.getInstance().putMessages(messagesArr, true, true, false, MediaController.getInstance().getAutodownloadMask());
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList);
                            }
                        }
                    }
                }

                boolean updateDialogs = false;
                if (!editingMessages.isEmpty()) {
                    for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : editingMessages.entrySet()) {
                        Long dialog_id = pair.getKey();
                        ArrayList<MessageObject> arrayList = pair.getValue();
                        MessageObject oldObject = dialogMessage.get(dialog_id);
                        if (oldObject != null) {
                            for (int a = 0; a < arrayList.size(); a++) {
                                MessageObject newMessage = arrayList.get(a);
                                if (oldObject.getId() == newMessage.getId()) {
                                    dialogMessage.put(dialog_id, newMessage);
                                    updateDialogs = true;
                                    break;
                                }
                            }
                        }
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.replaceMessagesObjects, dialog_id, arrayList);
                    }
                }
                if (!messages.isEmpty()) {
                    for (HashMap.Entry<Long, ArrayList<MessageObject>> entry : messages.entrySet()) {
                        Long key = entry.getKey();
                        ArrayList<MessageObject> value = entry.getValue();
                        updateInterfaceWithMessages(key, value);
                    }
                    updateDialogs = true;
                }
                if (updateDialogs) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                }

                if (printChangedArg) {
                    updateMask |= UPDATE_MASK_USER_PRINT;
                }
                if (!contactsIds.isEmpty()) {
                    updateMask |= UPDATE_MASK_NAME;
                    updateMask |= UPDATE_MASK_USER_PHONE;
                }
                if (!chatInfoToUpdate.isEmpty()) {
                    for (int a = 0; a < chatInfoToUpdate.size(); a++) {
                        TLRPC.ChatParticipants info = chatInfoToUpdate.get(a);
                        MessagesStorage.getInstance().updateChatParticipants(info);
                    }
                }
                if (channelViews.size() != 0) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedMessagesViews, channelViews);
                }
                if (updateMask != 0) {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, updateMask);
                }
            }
        });

        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        int updateMask = 0;
                        if (markAsReadMessagesInbox.size() != 0 || markAsReadMessagesOutbox.size() != 0) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesRead, markAsReadMessagesInbox, markAsReadMessagesOutbox);
                            NotificationsController.getInstance().processReadMessages(markAsReadMessagesInbox, 0, 0, 0, false);
                            for (int b = 0; b < markAsReadMessagesInbox.size(); b++) {
                                int key = markAsReadMessagesInbox.keyAt(b);
                                long messageId = markAsReadMessagesInbox.get(key);
                                TLRPC.Dialog dialog = dialogs_dict.get((long) key);
                                if (dialog != null && dialog.top_message <= (int) messageId) {
                                    MessageObject obj = dialogMessage.get(dialog.id);
                                    if (obj != null) {
                                        obj.setIsRead();
                                        updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                                    }
                                }
                            }
                            for (int b = 0; b < markAsReadMessagesOutbox.size(); b++) {
                                int key = markAsReadMessagesOutbox.keyAt(b);
                                int messageId = markAsReadMessagesOutbox.get(key);
                                TLRPC.Dialog dialog = dialogs_dict.get((long) key);
                                if (dialog != null && dialog.top_message <= messageId) {
                                    MessageObject obj = dialogMessage.get(dialog.id);
                                    if (obj != null) {
                                        obj.setIsRead();
                                        updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                                    }
                                }
                            }
                        }
                        if (!markAsReadEncrypted.isEmpty()) {
                            for (HashMap.Entry<Integer, Integer> entry : markAsReadEncrypted.entrySet()) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesReadEncrypted, entry.getKey(), entry.getValue());
                                long dialog_id = (long) (entry.getKey()) << 32;
                                TLRPC.Dialog dialog = dialogs_dict.get(dialog_id);
                                if (dialog != null) {
                                    MessageObject message = dialogMessage.get(dialog_id);
                                    if (message != null && message.messageOwner.date <= entry.getValue()) {
                                        message.setIsRead();
                                        updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                                    }
                                }
                            }
                        }
                        if (!markAsReadMessages.isEmpty()) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesReadContent, markAsReadMessages);
                        }
                        if (deletedMessages.size() != 0) {
                            for (int a = 0; a < deletedMessages.size(); a++) {
                                int key = deletedMessages.keyAt(a);
                                ArrayList<Integer> arrayList = deletedMessages.get(key);
                                if (arrayList == null) {
                                    continue;
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesDeleted, arrayList, key);
                                if (key == 0) {
                                    for (int b = 0; b < arrayList.size(); b++) {
                                        Integer id = arrayList.get(b);
                                        MessageObject obj = dialogMessagesByIds.get(id);
                                        if (obj != null) {
                                            obj.deleted = true;
                                        }
                                    }
                                } else {
                                    MessageObject obj = dialogMessage.get((long) -key);
                                    if (obj != null) {
                                        for (int b = 0; b < arrayList.size(); b++) {
                                            if (obj.getId() == arrayList.get(b)) {
                                                obj.deleted = true;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                            NotificationsController.getInstance().removeDeletedMessagesFromNotifications(deletedMessages);
                        }
                        if (updateMask != 0) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, updateMask);
                        }
                    }
                });
            }
        });

        if (!webPages.isEmpty()) {
            MessagesStorage.getInstance().putWebPages(webPages);
        }
        if (markAsReadMessagesInbox.size() != 0 || markAsReadMessagesOutbox.size() != 0 || !markAsReadEncrypted.isEmpty()) {
            if (markAsReadMessagesInbox.size() != 0) {
                MessagesStorage.getInstance().updateDialogsWithReadMessages(markAsReadMessagesInbox, true);
            }
            MessagesStorage.getInstance().markMessagesAsRead(markAsReadMessagesInbox, markAsReadMessagesOutbox, markAsReadEncrypted, true);
        }
        if (!markAsReadMessages.isEmpty()) {
            MessagesStorage.getInstance().markMessagesContentAsRead(markAsReadMessages);
        }
        if (deletedMessages.size() != 0) {
            for (int a = 0; a < deletedMessages.size(); a++) {
                int key = deletedMessages.keyAt(a);
                ArrayList<Integer> arrayList = deletedMessages.get(key);
                MessagesStorage.getInstance().markMessagesAsDeleted(arrayList, true, key);
                MessagesStorage.getInstance().updateDialogsWithDeletedMessages(arrayList, true, key);
            }
        }
        if (!tasks.isEmpty()) {
            for (int a = 0; a < tasks.size(); a++) {
                TLRPC.TL_updateEncryptedMessagesRead update = tasks.get(a);
                MessagesStorage.getInstance().createTaskForSecretChat(update.chat_id, update.max_date, update.date, 1, null);
            }
        }

        return true;
    }

    private boolean isNotifySettingsMuted(TLRPC.PeerNotifySettings settings) {
        return settings instanceof TLRPC.TL_peerNotifySettings && settings.mute_until > ConnectionsManager.getInstance().getCurrentTime();
    }

    public boolean isDialogMuted(long dialog_id) {
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
        int mute_type = preferences.getInt("notify2_" + dialog_id, 0);
        if (mute_type == 2) {
            return true;
        } else if (mute_type == 3) {
            int mute_until = preferences.getInt("notifyuntil_" + dialog_id, 0);
            if (mute_until >= ConnectionsManager.getInstance().getCurrentTime()) {
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

    protected static void addNewGifToRecent(TLRPC.Document document, int date) {
        ArrayList<MediaController.SearchImage> arrayList = new ArrayList<>();
        MediaController.SearchImage searchImage = new MediaController.SearchImage();
        searchImage.type = 2;
        searchImage.document = document;
        searchImage.date = date;
        searchImage.id = "" + searchImage.document.id;
        arrayList.add(searchImage);
        MessagesStorage.getInstance().putWebRecent(arrayList);
    }

    protected void updateInterfaceWithMessages(final long uid, final ArrayList<MessageObject> messages, boolean isBroadcast) {
        if (messages == null || messages.isEmpty()) {
            return;
        }

        boolean isEncryptedChat = ((int) uid) == 0;
        MessageObject lastMessage = null;
        int channelId = 0;
        for (int a = 0; a < messages.size(); a++) {
            MessageObject message = messages.get(a);
            if (lastMessage == null || (!isEncryptedChat && message.getId() > lastMessage.getId() || (isEncryptedChat || message.getId() < 0 && lastMessage.getId() < 0) && message.getId() < lastMessage.getId()) || message.messageOwner.date > lastMessage.messageOwner.date) {
                if (message.messageOwner.to_id.channel_id == 0 || message.isMegagroup() || message.isImportant()) {
                    lastMessage = message;
                }
                if (message.messageOwner.to_id.channel_id != 0) {
                    channelId = message.messageOwner.to_id.channel_id;
                }
            }
            if (message.isOut() && message.isNewGif() && !message.isSending() && !message.isForwarded()) {
                addNewGifToRecent(message.messageOwner.media.document, message.messageOwner.date);
            }
        }
        MessagesQuery.loadReplyMessagesForMessages(messages, uid);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didReceivedNewMessages, uid, messages);

        if (lastMessage == null) {
            return;
        }
        TLRPC.Dialog dialog = dialogs_dict.get(uid);
        if (lastMessage.messageOwner.action instanceof TLRPC.TL_messageActionChatMigrateTo) {
            if (dialog != null) {
                dialogs.remove(dialog);
                dialogsServerOnly.remove(dialog);
                dialogsGroupsOnly.remove(dialog);
                dialogs_dict.remove(dialog.id);
                dialogs_read_inbox_max.remove(dialog.id);
                nextDialogsCacheOffset--;
                dialogMessage.remove(dialog.id);
                MessageObject object = dialogMessagesByIds.remove(dialog.top_message);
                if (object != null && object.messageOwner.random_id != 0) {
                    dialogMessagesByRandomIds.remove(object.messageOwner.random_id);
                }
                dialog.top_message = 0;
                NotificationsController.getInstance().removeNotificationsForDialog(dialog.id);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.needReloadRecentDialogsSearch);
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
                if (!ChatObject.isChannel(chat)) {
                    dialog = new TLRPC.TL_dialog();
                } else {
                    dialog = new TLRPC.TL_dialogChannel();
                }
                dialog.id = uid;
                dialog.unread_count = 0;
                dialog.top_message = lastMessage.getId();
                dialog.last_message_date = lastMessage.messageOwner.date;
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
                    !dialogMessage.containsKey(uid) || dialog.top_message < 0 || dialog.last_message_date <= lastMessage.messageOwner.date) {
                MessageObject object = dialogMessagesByIds.remove(dialog.top_message);
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
            dialogsServerOnly.clear();
            dialogsGroupsOnly.clear();
            Collections.sort(dialogs, new Comparator<TLRPC.Dialog>() {
                @Override
                public int compare(TLRPC.Dialog tl_dialog, TLRPC.Dialog tl_dialog2) {
                    if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                        return 0;
                    } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            });
            for (int a = 0; a < dialogs.size(); a++) {
                TLRPC.Dialog d = dialogs.get(a);
                int high_id = (int) (d.id >> 32);
                if ((int) d.id != 0 && high_id != 1) {
                    dialogsServerOnly.add(d);
                    if (d instanceof TLRPC.TL_dialog) {
                        if (d.id < 0) {
                            dialogsGroupsOnly.add(d);
                        }
                    } else if (d instanceof TLRPC.TL_dialogChannel) {
                        int lower_id = (int) d.id;
                        TLRPC.Chat chat = getChat(-lower_id);
                        if (chat != null && (chat.megagroup && chat.editor || chat.creator)) {
                            dialogsGroupsOnly.add(d);
                        }
                    }
                }
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
        AlertDialog.Builder builder = new AlertDialog.Builder(fragment.getParentActivity());
        builder.setTitle(LocaleController.getString("AppName", R.string.AppName));
        builder.setPositiveButton(LocaleController.getString("OK", R.string.OK), null);
        builder.setMessage(reason);
        fragment.showDialog(builder.create());
    }

    public static boolean checkCanOpenChat(Bundle bundle, BaseFragment fragment) {
        if (bundle == null || fragment == null) {
            return true;
        }
        TLRPC.User user = null;
        TLRPC.Chat chat = null;
        int user_id = bundle.getInt("user_id", 0);
        int chat_id = bundle.getInt("chat_id", 0);
        if (user_id != 0) {
            user = MessagesController.getInstance().getUser(user_id);
        } else if (chat_id != 0) {
            chat = MessagesController.getInstance().getChat(chat_id);
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
        return true;
    }

    public static void openChatOrProfileWith(TLRPC.User user, TLRPC.Chat chat, BaseFragment fragment, int type) {
        if (user == null && chat == null || fragment == null) {
            return;
        }
        String reason = null;
        boolean closeLast = false;
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
            } else {
                fragment.presentFragment(new ChatActivity(args), closeLast);
            }
        }
    }

    public static void openByUserName(String username, final BaseFragment fragment, final int type) {
        if (username == null || fragment == null) {
            return;
        }
        TLRPC.User user = getInstance().getUser(username);
        if (user != null) {
            openChatOrProfileWith(user, null, fragment, type);
        } else {
            if (fragment.getParentActivity() == null) {
                return;
            }
            final ProgressDialog progressDialog = new ProgressDialog(fragment.getParentActivity());
            progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
            progressDialog.setCanceledOnTouchOutside(false);
            progressDialog.setCancelable(false);

            TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
            req.username = username;
            final int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                progressDialog.dismiss();
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            fragment.setVisibleDialog(null);
                            if (error == null) {
                                TLRPC.TL_contacts_resolvedPeer res = (TLRPC.TL_contacts_resolvedPeer) response;
                                getInstance().putUsers(res.users, false);
                                getInstance().putChats(res.chats, false);
                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, false, true);
                                if (!res.chats.isEmpty()) {
                                    openChatOrProfileWith(null, res.chats.get(0), fragment, 1);
                                } else if (!res.users.isEmpty()) {
                                    openChatOrProfileWith(res.users.get(0), null, fragment, type);
                                }
                            } else {
                                if (fragment != null && fragment.getParentActivity() != null) {
                                    try {
                                        Toast.makeText(fragment.getParentActivity(), LocaleController.getString("NoUsernameFound", R.string.NoUsernameFound), Toast.LENGTH_SHORT).show();
                                    } catch (Exception e) {
                                        FileLog.e("tmessages", e);
                                    }
                                }
                            }
                        }
                    });
                }
            });
            progressDialog.setButton(DialogInterface.BUTTON_NEGATIVE, LocaleController.getString("Cancel", R.string.Cancel), new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    ConnectionsManager.getInstance().cancelRequest(reqId, true);
                    try {
                        dialog.dismiss();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    if (fragment != null) {
                        fragment.setVisibleDialog(null);
                    }
                }
            });
            fragment.setVisibleDialog(progressDialog);
            progressDialog.show();
        }
    }
}
