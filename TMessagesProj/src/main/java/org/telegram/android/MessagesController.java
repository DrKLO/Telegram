/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.android;

import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.text.Html;
import android.util.SparseArray;

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.ApplicationLoader;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class MessagesController implements NotificationCenter.NotificationCenterDelegate {

    private ConcurrentHashMap<Integer, TLRPC.Chat> chats = new ConcurrentHashMap<Integer, TLRPC.Chat>(100, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.EncryptedChat> encryptedChats = new ConcurrentHashMap<Integer, TLRPC.EncryptedChat>(10, 1.0f, 2);
    private ConcurrentHashMap<Integer, TLRPC.User> users = new ConcurrentHashMap<Integer, TLRPC.User>(100, 1.0f, 2);

    public ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<TLRPC.TL_dialog>();
    public ArrayList<TLRPC.TL_dialog> dialogsServerOnly = new ArrayList<TLRPC.TL_dialog>();
    public ConcurrentHashMap<Long, TLRPC.TL_dialog> dialogs_dict = new ConcurrentHashMap<Long, TLRPC.TL_dialog>(100, 1.0f, 2);
    public HashMap<Integer, MessageObject> dialogMessage = new HashMap<Integer, MessageObject>();
    public ConcurrentHashMap<Long, ArrayList<PrintingUser>> printingUsers = new ConcurrentHashMap<Long, ArrayList<PrintingUser>>(20, 1.0f, 2);
    public HashMap<Long, CharSequence> printingStrings = new HashMap<Long, CharSequence>();
    public HashMap<Long, Boolean> sendingTypings = new HashMap<Long, Boolean>();
    public ConcurrentHashMap<Integer, Integer> onlinePrivacy = new ConcurrentHashMap<Integer, Integer>(20, 1.0f, 2);
    private int lastPrintingStringCount = 0;

    public boolean loadingBlockedUsers = false;
    public ArrayList<Integer> blockedUsers = new ArrayList<Integer>();

    private ArrayList<TLRPC.Updates> updatesQueue = new ArrayList<TLRPC.Updates>();
    private long updatesStartWaitTime = 0;
    private ArrayList<Integer> loadingFullUsers = new ArrayList<Integer>();
    private ArrayList<Integer> loadedFullUsers = new ArrayList<Integer>();
    private ArrayList<Integer> loadingFullChats = new ArrayList<Integer>();
    private ArrayList<Integer> loadedFullChats = new ArrayList<Integer>();

    private boolean gettingNewDeleteTask = false;
    private int currentDeletingTaskTime = 0;
    private ArrayList<Integer> currentDeletingTaskMids = null;
    private Runnable currentDeleteTaskRunnable = null;

    public int totalDialogsCount = 0;
    public boolean loadingDialogs = false;
    public boolean dialogsEndReached = false;
    public boolean gettingDifference = false;
    public boolean gettingDifferenceAgain = false;
    public boolean updatingState = false;
    public boolean firstGettingTask = false;
    public boolean registeringForPush = false;

    private long lastStatusUpdateTime = 0;
    private long statusRequest = 0;
    private int statusSettingState = 0;
    private boolean offlineSent = false;
    private String uploadingAvatar = null;

    public boolean enableJoined = true;
    public int fontSize = AndroidUtilities.dp(16);
    public int maxGroupCount = 200;
    public int maxBroadcastCount = 100;

    private class UserActionUpdates extends TLRPC.Updates {

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
    public static final int UPDATE_MASK_ALL = UPDATE_MASK_AVATAR | UPDATE_MASK_STATUS | UPDATE_MASK_NAME | UPDATE_MASK_CHAT_AVATAR | UPDATE_MASK_CHAT_NAME | UPDATE_MASK_CHAT_MEMBERS | UPDATE_MASK_USER_PRINT | UPDATE_MASK_USER_PHONE | UPDATE_MASK_READ_DIALOG_MESSAGE | UPDATE_MASK_PHONE;

    public static class PrintingUser {
        public long lastTime;
        public int userId;
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
        maxGroupCount = preferences.getInt("maxGroupCount", 200);
        maxBroadcastCount = preferences.getInt("maxBroadcastCount", 100);
        fontSize = preferences.getInt("fons_size", AndroidUtilities.isTablet() ? 18 : 16);
    }

    public void updateConfig(final TLRPC.TL_config config) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                maxBroadcastCount = config.broadcast_size_max;
                maxGroupCount = config.chat_size_max;
                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Activity.MODE_PRIVATE);
                SharedPreferences.Editor editor = preferences.edit();
                editor.putInt("maxGroupCount", maxGroupCount);
                editor.putInt("maxBroadcastCount", maxBroadcastCount);
                editor.commit();
            }
        });
    }

    public void addSupportUser() {
        TLRPC.TL_userForeign user = new TLRPC.TL_userForeign();
        user.phone = "333";
        user.id = 333000;
        user.first_name = "Telegram";
        user.last_name = "";
        user.status = null;
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        putUser(user, true);

        user = new TLRPC.TL_userForeign();
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
            return null;
        }
        TLRPC.InputUser inputUser = null;
        if (user.id == UserConfig.getClientUserId()) {
            inputUser = new TLRPC.TL_inputUserSelf();
        } else if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
            inputUser = new TLRPC.TL_inputUserForeign();
            inputUser.user_id = user.id;
            inputUser.access_hash = user.access_hash;
        } else {
            inputUser = new TLRPC.TL_inputUserContact();
            inputUser.user_id = user.id;
        }
        return inputUser;
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == NotificationCenter.FileDidUpload) {
            final String location = (String)args[0];
            final TLRPC.InputFile file = (TLRPC.InputFile)args[1];
            final TLRPC.InputEncryptedFile encryptedFile = (TLRPC.InputEncryptedFile)args[2];

            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
                req.caption = "";
                req.crop = new TLRPC.TL_inputPhotoCropAuto();
                req.file = file;
                req.geo_point = new TLRPC.TL_inputGeoPointEmpty();
                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
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
            final boolean enc = (Boolean) args[1];

            if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
                uploadingAvatar = null;
            }
        } else if (id == NotificationCenter.messageReceivedByServer) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = dialogMessage.get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer)args[1];
                dialogMessage.remove(msgId);
                dialogMessage.put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;

                long uid;
                if (obj.messageOwner.to_id.chat_id != 0) {
                    uid = -obj.messageOwner.to_id.chat_id;
                } else {
                    if (obj.messageOwner.to_id.user_id == UserConfig.getClientUserId()) {
                        obj.messageOwner.to_id.user_id = obj.messageOwner.from_id;
                    }
                    uid = obj.messageOwner.to_id.user_id;
                }

                TLRPC.TL_dialog dialog = dialogs_dict.get(uid);
                if (dialog != null) {
                    if (dialog.top_message == msgId) {
                        dialog.top_message = newMsgId;
                    }
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        } else {
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
            NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
        }
    }

    public void cleanUp() {
        ContactsController.getInstance().cleanup();
        MediaController.getInstance().cleanup();
        NotificationsController.getInstance().cleanup();
        SendMessagesHelper.getInstance().cleanUp();
        SecretChatHelper.getInstance().cleanUp();

        dialogs_dict.clear();
        dialogs.clear();
        dialogsServerOnly.clear();
        users.clear();
        chats.clear();
        dialogMessage.clear();
        printingUsers.clear();
        printingStrings.clear();
        onlinePrivacy.clear();
        totalDialogsCount = 0;
        lastPrintingStringCount = 0;
        updatesQueue.clear();
        blockedUsers.clear();
        sendingTypings.clear();
        loadingFullUsers.clear();
        loadedFullUsers.clear();
        loadingFullUsers.clear();
        loadedFullUsers.clear();

        updatesStartWaitTime = 0;
        currentDeletingTaskTime = 0;
        currentDeletingTaskMids = null;
        gettingNewDeleteTask = false;
        loadingDialogs = false;
        dialogsEndReached = false;
        gettingDifference = false;
        gettingDifferenceAgain = false;
        loadingBlockedUsers = false;
        firstGettingTask = false;
        updatingState = false;
        lastStatusUpdateTime = 0;
        offlineSent = false;
        registeringForPush = false;
        uploadingAvatar = null;
        statusRequest = 0;
        statusSettingState = 0;

        if (currentDeleteTaskRunnable != null) {
            Utilities.stageQueue.cancelRunnable(currentDeleteTaskRunnable);
            currentDeleteTaskRunnable = null;
        }

        addSupportUser();
    }

    public TLRPC.User getUser(Integer id) {
        return users.get(id);
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
            ArrayList<TLObject> result = new ArrayList<TLObject>();
            MessagesStorage.getInstance().getEncryptedChat(chat_id, semaphore, result);
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (result.size() == 2) {
                chat = (TLRPC.EncryptedChat)result.get(0);
                TLRPC.User user = (TLRPC.User)result.get(1);
                putEncryptedChat(chat, false);
                putUser(user, true);
            }
        }
        return chat;
    }

    public boolean putUser(TLRPC.User user, boolean fromCache) {
        if (user == null) {
            return false;
        }
        fromCache = fromCache && user.id / 1000 != 333 && user.id != 777000;
        TLRPC.User oldUser = users.get(user.id);
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
        }
        return false;
    }

    public void putUsers(ArrayList<TLRPC.User> users, boolean fromCache) {
        if (users == null || users.isEmpty()) {
            return;
        }
        boolean updateStatus = false;
        for (TLRPC.User user : users) {
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
        if (fromCache) {
            chats.putIfAbsent(chat.id, chat);
        } else {
            chats.put(chat.id, chat);
        }
    }

    public void putChats(ArrayList<TLRPC.Chat> chats, boolean fromCache) {
        if (chats == null || chats.isEmpty()) {
            return;
        }
        for (TLRPC.Chat chat : chats) {
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
        for (TLRPC.EncryptedChat encryptedChat : encryptedChats) {
            putEncryptedChat(encryptedChat, fromCache);
        }
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

    public void loadFullChat(final int chat_id, final int classGuid) {
        if (loadingFullChats.contains(chat_id) || loadedFullChats.contains(chat_id)) {
            return;
        }
        loadingFullChats.add(chat_id);
        TLRPC.TL_messages_getFullChat req = new TLRPC.TL_messages_getFullChat();
        req.chat_id = chat_id;
        long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    final TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull) response;
                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                    MessagesStorage.getInstance().updateChatInfo(chat_id, res.full_chat.participants, false);
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingFullChats.remove((Integer)chat_id);
                            loadedFullChats.add(chat_id);

                            putUsers(res.users, false);
                            putChats(res.chats, false);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, chat_id, res.full_chat.participants);
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
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

    public void loadFullUser(final TLRPC.User user, final int classGuid) {
        if (user == null || loadingFullUsers.contains(user.id) || loadedFullUsers.contains(user.id)) {
            return;
        }
        loadingFullUsers.add(user.id);
        TLRPC.TL_users_getFullUser req = new TLRPC.TL_users_getFullUser();
        req.id = getInputUser(user);
        long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(final TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingFullUsers.remove((Integer)user.id);
                            loadedFullUsers.add(user.id);
                            String names = user.first_name + user.last_name + user.username;
                            TLRPC.TL_userFull userFull = (TLRPC.TL_userFull)response;
                            ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                            users.add(userFull.user);
                            putUsers(users, false);
                            MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                            if (!names.equals(userFull.user.first_name + userFull.user.last_name + userFull.user.username)) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_NAME);
                            }
                        }
                    });
                } else {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadingFullUsers.remove((Integer)user.id);
                        }
                    });
                }
            }
        });
        ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
    }

    protected void processNewDifferenceParams(int seq, int pts, int date) {
        if (MessagesStorage.lastSeqValue + 1 == seq) {
            if (seq != -1) {
                MessagesStorage.lastSeqValue = seq;
            }
            if (date != -1) {
                MessagesStorage.lastDateValue = date;
            }
            if (pts != -1) {
                MessagesStorage.lastPtsValue = pts;
            }
            MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
        } else if (MessagesStorage.lastSeqValue != seq) {
            if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                if (updatesStartWaitTime == 0) {
                    updatesStartWaitTime = System.currentTimeMillis();
                }
                UserActionUpdates updates = new UserActionUpdates();
                updates.seq = seq;
                updatesQueue.add(updates);
            } else {
                getDifference();
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
                    deleteMessages(currentDeletingTaskMids, null, null);

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
                        Utilities.stageQueue.postRunnable(currentDeleteTaskRunnable, (long)Math.abs(currentServerTime - currentDeletingTaskTime) * 1000);
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
            req.max_id = (int)max_id;
            req.user_id = getInputUser(user);
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.photos_Photos res = (TLRPC.photos_Photos) response;
                        processLoadedUserPhotos(res, uid, offset, count, max_id, fromCache, classGuid);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void blockUser(int user_id) {
        final TLRPC.User user = getUser(user_id);
        if (user == null || MessagesController.getInstance().blockedUsers.contains(user_id)) {
            return;
        }
        blockedUsers.add(user_id);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.blockedUsersDidLoaded);
        TLRPC.TL_contacts_block req = new TLRPC.TL_contacts_block();
        req.id = MessagesController.getInputUser(user);
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    ArrayList<Integer> ids = new ArrayList<Integer>();
                    ids.add(user.id);
                    MessagesStorage.getInstance().putBlockedUsers(ids, false);
                }
            }
        });
    }

    public void unblockUser(int user_id) {
        TLRPC.TL_contacts_unblock req = new TLRPC.TL_contacts_unblock();
        final TLRPC.User user = MessagesController.getInstance().getUser(user_id);
        if (user == null) {
            return;
        }
        blockedUsers.remove((Integer)user.id);
        req.id = MessagesController.getInputUser(user);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.blockedUsersDidLoaded);
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    ArrayList<Integer> blocked = new ArrayList<Integer>();
                    ArrayList<TLRPC.User> users = null;
                    if (error == null) {
                        final TLRPC.contacts_Blocked res = (TLRPC.contacts_Blocked)response;
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
                    MessagesController.getInstance().putUsers(users, cache);
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
            TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
            if (user == null) {
                user = UserConfig.getCurrentUser();
            }
            if (user == null) {
                return;
            }
            if (user != null) {
                user.photo = UserConfig.getCurrentUser().photo;
            }
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.mainUserInfoChanged);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.User user = MessagesController.getInstance().getUser(UserConfig.getClientUserId());
                        if (user == null) {
                            user = UserConfig.getCurrentUser();
                            MessagesController.getInstance().putUser(user, false);
                        } else {
                            UserConfig.setCurrentUser(user);
                        }
                        if (user == null) {
                            return;
                        }
                        MessagesStorage.getInstance().clearUserPhotos(user.id);
                        ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                        users.add(user);
                        MessagesStorage.getInstance().putUsersAndChats(users, null, false, true);
                        user.photo = (TLRPC.UserProfilePhoto)response;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_ALL);
                                UserConfig.saveConfig(true);
                            }
                        });
                    }
                }
            });
        } else {
            TLRPC.TL_photos_deletePhotos req = new TLRPC.TL_photos_deletePhotos();
            req.id.add(photo);
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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

    public void processLoadedMedia(final TLRPC.messages_Messages res, final long uid, int offset, int count, int max_id, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache && res.messages.isEmpty() && lower_part != 0) {
            loadMedia(uid, offset, count, max_id, false, classGuid);
        } else {
            if (!fromCache) {
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                MessagesStorage.getInstance().putMedia(uid, res.messages);
            }

            final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
            for (TLRPC.User u : res.users) {
                usersLocal.put(u.id, u);
            }
            final ArrayList<MessageObject> objects = new ArrayList<MessageObject>();
            for (TLRPC.Message message : res.messages) {
                objects.add(new MessageObject(message, usersLocal));
            }

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    int totalCount;
                    if (res instanceof TLRPC.TL_messages_messagesSlice) {
                        totalCount = res.count;
                    } else {
                        totalCount = res.messages.size();
                    }
                    putUsers(res.users, fromCache);
                    for (TLRPC.Chat chat : res.chats) {
                        putChat(chat, fromCache);
                    }
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.mediaDidLoaded, uid, totalCount, objects, fromCache, classGuid);
                }
            });
        }
    }

    public void loadMedia(final long uid, final int offset, final int count, final int max_id, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance().loadMedia(uid, offset, count, max_id, classGuid);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.offset = offset;
            req.limit = count;
            req.max_id = max_id;
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            req.q = "";
            if (uid < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = getUser(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                }
                req.peer.user_id = lower_part;
            }
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        processLoadedMedia(res, uid, offset, count, max_id, false, classGuid);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedMediaCount(final int count, final long uid, final int classGuid, final boolean fromCache) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                int lower_part = (int)uid;
                if (fromCache && count == -1 && lower_part != 0) {
                    getMediaCount(uid, classGuid, false);
                } else {
                    if (!fromCache) {
                        MessagesStorage.getInstance().putMediaCount(uid, count);
                    }
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.mediaCountDidLoaded, uid, (fromCache && count == -1 ? 0 : count), fromCache);
                }
            }
        });
    }

    public void getMediaCount(final long uid, final int classGuid, boolean fromCache) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance().getMediaCount(uid, classGuid);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.offset = 0;
            req.limit = 1;
            req.max_id = 0;
            req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            req.q = "";
            if (uid < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = getUser(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                }
                req.peer.user_id = lower_part;
            }
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                putUsers(res.users, false);
                                for (TLRPC.Chat chat : res.chats) {
                                    putChat(chat, false);
                                }
                            }
                        });

                        if (res instanceof TLRPC.TL_messages_messagesSlice) {
                            processLoadedMediaCount(res.count, uid, classGuid, false);
                        } else {
                            processLoadedMediaCount(res.messages.size(), uid, classGuid, false);
                        }
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void uploadAndApplyUserAvatar(TLRPC.PhotoSize bigPhoto) {
        if (bigPhoto != null) {
            uploadingAvatar = FileLoader.getInstance().getDirectory(FileLoader.MEDIA_DIR_CACHE) + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
            FileLoader.getInstance().uploadFile(uploadingAvatar, false, true);
        }
    }

    public void deleteMessages(ArrayList<Integer> messages, ArrayList<Long> randoms, TLRPC.EncryptedChat encryptedChat) {
        if (messages == null) {
            return;
        }
        for (Integer id : messages) {
            MessageObject obj = dialogMessage.get(id);
            if (obj != null) {
                obj.deleted = true;
            }
        }
        MessagesStorage.getInstance().markMessagesAsDeleted(messages, true);
        MessagesStorage.getInstance().updateDialogsWithDeletedMessages(messages, true);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesDeleted, messages);

        if (randoms != null && encryptedChat != null && !randoms.isEmpty()) {
            SecretChatHelper.getInstance().sendMessagesDeleteMessage(encryptedChat, randoms, null);
        }

        ArrayList<Integer> toSend = new ArrayList<Integer>();
        for (Integer mid : messages) {
            if (mid > 0) {
                toSend.add(mid);
            }
        }
        if (toSend.isEmpty()) {
            return;
        }
        TLRPC.TL_messages_deleteMessages req = new TLRPC.TL_messages_deleteMessages();
        req.id = messages;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
    }

    public void deleteDialog(final long did, int offset, final boolean onlyHistory) {
        if (offset == 0) {
            MessagesStorage.getInstance().deleteDialog(did, onlyHistory);
        }

        int lower_part = (int)did;
        int high_id = (int)(did >> 32);

        if (offset == 0) {
            TLRPC.TL_dialog dialog = dialogs_dict.get(did);
            if (dialog != null) {
                if (!onlyHistory) {
                    dialogs.remove(dialog);
                    dialogsServerOnly.remove(dialog);
                    dialogs_dict.remove(did);
                    totalDialogsCount--;
                } else {
                    dialog.unread_count = 0;
                }
                dialogMessage.remove(dialog.top_message);
            }
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationsController.getInstance().processReadMessages(null, did, 0, Integer.MAX_VALUE, false);
                            HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();
                            dialogsToUpdate.put(did, 0);
                            NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                        }
                    });
                }
            });
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.removeAllMessagesFromDialog, did);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
        }

        if (high_id == 1) {
            return;
        }

        if (lower_part != 0) {
            TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
            req.offset = offset;
            if (did < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = getUser(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                }
                req.peer.user_id = lower_part;
            }
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                        if (res.offset > 0) {
                            deleteDialog(did, res.offset, onlyHistory);
                        }
                        processNewDifferenceParams(res.seq, res.pts, -1);
                    }
                }
            });
        } else {
            if (onlyHistory) {
                SecretChatHelper.getInstance().sendClearHistoryMessage(getEncryptedChat(high_id), null);
            } else {
                SecretChatHelper.getInstance().declineSecretChat(high_id);
            }
        }
    }

    public void loadChatInfo(final int chat_id, Semaphore semaphore) {
        MessagesStorage.getInstance().loadChatInfo(chat_id, semaphore);
    }

    public void processChatInfo(final int chat_id, final TLRPC.ChatParticipants info, final ArrayList<TLRPC.User> usersArr, final boolean fromCache) {
        if (fromCache && chat_id > 0) {
            loadFullChat(chat_id, 0);
        }
        if (info != null) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    putUsers(usersArr, fromCache);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, chat_id, info);
                }
            });
        }
    }

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();

        checkDeletingTask(false);

        if (UserConfig.isClientActivated()) {
            if (ConnectionsManager.getInstance().getPauseTime() == 0 && ApplicationLoader.isScreenOn && !ApplicationLoader.mainInterfacePaused) {
                if (statusSettingState != 1 && (lastStatusUpdateTime == 0 || lastStatusUpdateTime <= System.currentTimeMillis() - 55000 || offlineSent)) {
                    statusSettingState = 1;

                    if (statusRequest != 0) {
                        ConnectionsManager.getInstance().cancelRpc(statusRequest, true);
                    }

                    TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                    req.offline = false;
                    statusRequest = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
            } else if (statusSettingState != 2 && !offlineSent && ConnectionsManager.getInstance().getPauseTime() <= System.currentTimeMillis() - 2000) {
                statusSettingState = 2;
                if (statusRequest != 0) {
                    ConnectionsManager.getInstance().cancelRpc(statusRequest, true);
                }
                TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                req.offline = true;
                statusRequest = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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

            if (updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 < currentTime) {
                FileLog.e("tmessages", "UPDATES WAIT TIMEOUT - CHECK QUEUE");
                processUpdatesQueue(0);
            }
        }
        if (!onlinePrivacy.isEmpty()) {
            ArrayList<Integer> toRemove = null;
            int currentServerTime = ConnectionsManager.getInstance().getCurrentTime();
            for (ConcurrentHashMap.Entry<Integer, Integer> entry : onlinePrivacy.entrySet()) {
                if (entry.getValue() < currentServerTime - 30) {
                    if (toRemove == null) {
                        toRemove = new ArrayList<Integer>();
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
        if (!printingUsers.isEmpty() || lastPrintingStringCount != printingUsers.size()) {
            boolean updated = false;
            ArrayList<Long> keys = new ArrayList<Long>(printingUsers.keySet());
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

    public void updatePrintingStrings() {
        final HashMap<Long, CharSequence> newPrintingStrings = new HashMap<Long, CharSequence>();

        ArrayList<Long> keys = new ArrayList<Long>(printingUsers.keySet());
        for (Long key : keys) {
            if (key > 0 || key.intValue() == 0) {
                newPrintingStrings.put(key, LocaleController.getString("Typing", R.string.Typing));
            } else {
                ArrayList<PrintingUser> arr = printingUsers.get(key);
                int count = 0;
                String label = "";
                for (PrintingUser pu : arr) {
                    TLRPC.User user = getUser(pu.userId);
                    if (user != null) {
                        if (label.length() != 0) {
                            label += ", ";
                        }
                        label += ContactsController.formatName(user.first_name, user.last_name);
                        count++;
                    }
                    if (count == 2) {
                        break;
                    }
                }
                if (label.length() != 0) {
                    if (count > 1) {
                        if (arr.size() > 2) {
                            newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, LocaleController.formatPluralString("AndMoreTyping", arr.size() - 2))));
                        } else {
                            newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, LocaleController.getString("AreTyping", R.string.AreTyping))));
                        }
                    } else {
                        newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, LocaleController.getString("IsTyping", R.string.IsTyping))));
                    }
                }
            }
        }

        lastPrintingStringCount = newPrintingStrings.size();

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                printingStrings = newPrintingStrings;
            }
        });
    }

    public void cancelTyping(long dialog_id) {
        sendingTypings.remove(dialog_id);
    }

    public void sendTyping(final long dialog_id, int classGuid) {
        if (dialog_id == 0) {
            return;
        }
        if (sendingTypings.get(dialog_id) != null) {
            return;
        }
        int lower_part = (int)dialog_id;
        int high_id = (int)(dialog_id >> 32);
        if (lower_part != 0) {
            if (high_id == 1) {
                return;
            }

            TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = getUser(lower_part);
                if (user != null) {
                    if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                        req.peer = new TLRPC.TL_inputPeerForeign();
                        req.peer.user_id = user.id;
                        req.peer.access_hash = user.access_hash;
                    } else {
                        req.peer = new TLRPC.TL_inputPeerContact();
                        req.peer.user_id = user.id;
                    }
                } else {
                    return;
                }
            }
            req.action = new TLRPC.TL_sendMessageTypingAction();
            sendingTypings.put(dialog_id, true);
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            sendingTypings.remove(dialog_id);
                        }
                    });
                }
            }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        } else {
            TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_setEncryptedTyping req = new TLRPC.TL_messages_setEncryptedTyping();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.typing = true;
                sendingTypings.put(dialog_id, true);
                long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        sendingTypings.remove(dialog_id);
                    }
                }, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public void loadMessages(final long dialog_id, final int count, final int max_id, boolean fromCache, int midDate, final int classGuid, final int load_type, final int last_message_id, final int first_message_id, final boolean allowCache) {
        int lower_part = (int)dialog_id;
        if (fromCache || lower_part == 0) {
            MessagesStorage.getInstance().getMessages(dialog_id, count, max_id, midDate, classGuid, load_type);
        } else {
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = getUser(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.user_id = user.id;
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                    req.peer.user_id = user.id;
                }
            }
            if (load_type == 3) {
                req.offset = -count / 2;
            } else if (load_type == 1) {
                req.offset = -count - 1;
            } else {
                req.offset = 0;
            }
            req.limit = count;
            req.max_id = max_id;
            long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        processLoadedMessages(res, dialog_id, count, max_id, false, classGuid, 0, last_message_id, first_message_id, 0, 0, load_type, allowCache);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedMessages(final TLRPC.messages_Messages messagesRes, final long dialog_id, final int count, final int max_id, final boolean isCache, final int classGuid,
                                      final int first_unread, final int last_message_id, final int first_message_id, final int unread_count, final int last_date, final int load_type, final boolean allowCache) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                int lower_id = (int)dialog_id;
                int high_id = (int)(dialog_id >> 32);
                if (!isCache && allowCache) {
                    MessagesStorage.getInstance().putMessages(messagesRes, dialog_id);
                }
                if (high_id != 1 && lower_id != 0 && isCache && messagesRes.messages.size() == 0 && (load_type == 0 || load_type == 2 || load_type == 3)) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadMessages(dialog_id, count, max_id, false, 0, classGuid, load_type, last_message_id, first_message_id, allowCache);
                        }
                    });
                    return;
                }
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
                for (TLRPC.User u : messagesRes.users) {
                    usersLocal.put(u.id, u);
                }
                final ArrayList<MessageObject> objects = new ArrayList<MessageObject>();
                for (TLRPC.Message message : messagesRes.messages) {
                    message.dialog_id = dialog_id;
                    objects.add(new MessageObject(message, usersLocal, 2));
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        putUsers(messagesRes.users, isCache);
                        putChats(messagesRes.chats, isCache);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesDidLoaded, dialog_id, count, objects, isCache, first_unread, last_message_id, first_message_id, unread_count, last_date, load_type);
                    }
                });
            }
        });
    }

    public void loadDialogs(final int offset, final int serverOffset, final int count, boolean fromCache) {
        if (loadingDialogs) {
            return;
        }
        loadingDialogs = true;
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);

        if (fromCache) {
            MessagesStorage.getInstance().getDialogs(offset, serverOffset, count);
        } else {
            TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
            req.offset = serverOffset;
            req.limit = count;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs) response;
                        processLoadedDialogs(dialogsRes, null, offset, serverOffset, count, false, false);
                    }
                }
            });
        }
    }

    private void applyDialogsNotificationsSettings(ArrayList<TLRPC.TL_dialog> dialogs) {
        SharedPreferences.Editor editor = null;
        for (TLRPC.TL_dialog dialog : dialogs) {
            if (dialog.peer != null && dialog.notify_settings instanceof TLRPC.TL_peerNotifySettings) {
                if (editor == null) {
                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                    editor = preferences.edit();
                }
                int dialog_id = dialog.peer.user_id;
                if (dialog_id == 0) {
                    dialog_id = -dialog.peer.chat_id;
                }
                if (dialog.notify_settings.mute_until != 0) {
                    editor.putInt("notify2_" + dialog_id, 2);
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
                    TLRPC.TL_dialog currentDialog = dialogs_dict.get(entry.getKey());
                    if (currentDialog != null) {
                        currentDialog.unread_count = entry.getValue();
                    }
                }
                NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            }
        });
    }

    public void processDialogsUpdate(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final HashMap<Long, TLRPC.TL_dialog> new_dialogs_dict = new HashMap<Long, TLRPC.TL_dialog>();
                final HashMap<Integer, MessageObject> new_dialogMessage = new HashMap<Integer, MessageObject>();
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
                final HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();

                for (TLRPC.User u : dialogsRes.users) {
                    usersLocal.put(u.id, u);
                }

                for (TLRPC.Message m : dialogsRes.messages) {
                    new_dialogMessage.put(m.id, new MessageObject(m, usersLocal, 0));
                }
                for (TLRPC.TL_dialog d : dialogsRes.dialogs) {
                    if (d.last_message_date == 0) {
                        MessageObject mess = new_dialogMessage.get(d.top_message);
                        if (mess != null) {
                            d.last_message_date = mess.messageOwner.date;
                        }
                    }
                    if (d.id == 0) {
                        if (d.peer instanceof TLRPC.TL_peerUser) {
                            d.id = d.peer.user_id;
                        } else if (d.peer instanceof TLRPC.TL_peerChat) {
                            d.id = -d.peer.chat_id;
                        }
                    }
                    new_dialogs_dict.put(d.id, d);
                    dialogsToUpdate.put(d.id, d.unread_count);
                }

                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        putUsers(dialogsRes.users, true);
                        putChats(dialogsRes.chats, true);

                        for (HashMap.Entry<Long, TLRPC.TL_dialog> pair : new_dialogs_dict.entrySet()) {
                            long key = pair.getKey();
                            TLRPC.TL_dialog value = pair.getValue();
                            TLRPC.TL_dialog currentDialog = dialogs_dict.get(key);
                            if (currentDialog == null) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                            } else {
                                currentDialog.unread_count = value.unread_count;
                                MessageObject oldMsg = dialogMessage.get(currentDialog.top_message);
                                if (oldMsg == null || currentDialog.top_message > 0) {
                                    if (oldMsg != null && oldMsg.deleted || value.top_message > currentDialog.top_message) {
                                        dialogs_dict.put(key, value);
                                        if (oldMsg != null) {
                                            dialogMessage.remove(oldMsg.messageOwner.id);
                                        }
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                } else {
                                    MessageObject newMsg = new_dialogMessage.get(value.top_message);
                                    if (oldMsg.deleted || newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                        dialogs_dict.put(key, value);
                                        dialogMessage.remove(oldMsg.messageOwner.id);
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                }
                            }
                        }

                        dialogs.clear();
                        dialogsServerOnly.clear();
                        dialogs.addAll(dialogs_dict.values());
                        Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                            @Override
                            public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                                if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                    return 0;
                                } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }
                        });
                        for (TLRPC.TL_dialog d : dialogs) {
                            int high_id = (int)(d.id >> 32);
                            if ((int)d.id != 0 && high_id != 1) {
                                dialogsServerOnly.add(d);
                            }
                        }
                        NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                    }
                });
             }
        });
    }

    public void processLoadedDialogs(final TLRPC.messages_Dialogs dialogsRes, final ArrayList<TLRPC.EncryptedChat> encChats, final int offset, final int serverOffset, final int count, final boolean isCache, final boolean resetEnd) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (isCache && dialogsRes.dialogs.size() == 0) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(dialogsRes.users, isCache);
                            loadingDialogs = false;
                            if (resetEnd) {
                                dialogsEndReached = false;
                            }
                            loadDialogs(offset, serverOffset, count, false);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                    });
                    return;
                }
                final HashMap<Long, TLRPC.TL_dialog> new_dialogs_dict = new HashMap<Long, TLRPC.TL_dialog>();
                final HashMap<Integer, MessageObject> new_dialogMessage = new HashMap<Integer, MessageObject>();
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
                int new_totalDialogsCount;

                if (!isCache) {
                    MessagesStorage.getInstance().putDialogs(dialogsRes);
                }

                if (dialogsRes instanceof TLRPC.TL_messages_dialogsSlice) {
                    TLRPC.TL_messages_dialogsSlice slice = (TLRPC.TL_messages_dialogsSlice)dialogsRes;
                    new_totalDialogsCount = slice.count;
                } else {
                    new_totalDialogsCount = dialogsRes.dialogs.size();
                }

                for (TLRPC.User u : dialogsRes.users) {
                    usersLocal.put(u.id, u);
                }

                for (TLRPC.Message m : dialogsRes.messages) {
                    new_dialogMessage.put(m.id, new MessageObject(m, usersLocal, 0));
                }
                for (TLRPC.TL_dialog d : dialogsRes.dialogs) {
                    if (d.last_message_date == 0) {
                        MessageObject mess = new_dialogMessage.get(d.top_message);
                        if (mess != null) {
                            d.last_message_date = mess.messageOwner.date;
                        }
                    }
                    if (d.id == 0) {
                        if (d.peer instanceof TLRPC.TL_peerUser) {
                            d.id = d.peer.user_id;
                        } else if (d.peer instanceof TLRPC.TL_peerChat) {
                            d.id = -d.peer.chat_id;
                        }
                    }
                    new_dialogs_dict.put(d.id, d);
                }

                final int arg1 = new_totalDialogsCount;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!isCache) {
                            applyDialogsNotificationsSettings(dialogsRes.dialogs);
                        }
                        putUsers(dialogsRes.users, isCache);
                        putChats(dialogsRes.chats, isCache);
                        if (encChats != null) {
                            for (TLRPC.EncryptedChat encryptedChat : encChats) {
                                if (encryptedChat instanceof TLRPC.TL_encryptedChat && AndroidUtilities.getMyLayerVersion(encryptedChat.layer) < SecretChatHelper.CURRENT_SECRET_CHAT_LAYER) {
                                    SecretChatHelper.getInstance().sendNotifyLayerMessage(encryptedChat, null);
                                }
                                putEncryptedChat(encryptedChat, true);
                            }
                        }
                        loadingDialogs = false;
                        totalDialogsCount = arg1;

                        for (HashMap.Entry<Long, TLRPC.TL_dialog> pair : new_dialogs_dict.entrySet()) {
                            long key = pair.getKey();
                            TLRPC.TL_dialog value = pair.getValue();
                            TLRPC.TL_dialog currentDialog = dialogs_dict.get(key);
                            if (currentDialog == null) {
                                dialogs_dict.put(key, value);
                                dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                            } else {
                                MessageObject oldMsg = dialogMessage.get(value.top_message);
                                if (oldMsg == null || currentDialog.top_message > 0) {
                                    if (oldMsg != null && oldMsg.deleted || value.top_message > currentDialog.top_message) {
                                        if (oldMsg != null) {
                                            dialogMessage.remove(oldMsg.messageOwner.id);
                                        }
                                        dialogs_dict.put(key, value);
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                } else {
                                    MessageObject newMsg = new_dialogMessage.get(value.top_message);
                                    if (oldMsg.deleted || newMsg == null || newMsg.messageOwner.date > oldMsg.messageOwner.date) {
                                        dialogMessage.remove(oldMsg.messageOwner.id);
                                        dialogs_dict.put(key, value);
                                        dialogMessage.put(value.top_message, new_dialogMessage.get(value.top_message));
                                    }
                                }
                            }
                        }

                        dialogs.clear();
                        dialogsServerOnly.clear();
                        dialogs.addAll(dialogs_dict.values());
                        Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                            @Override
                            public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                                if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                                    return 0;
                                } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                                    return 1;
                                } else {
                                    return -1;
                                }
                            }
                        });
                        for (TLRPC.TL_dialog d : dialogs) {
                            int high_id = (int)(d.id >> 32);
                            if ((int)d.id != 0 && high_id != 1) {
                                dialogsServerOnly.add(d);
                            }
                        }

                        dialogsEndReached = (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && !isCache;
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                    }
                });
            }
        });
    }

    public void markMessageAsRead(final long dialog_id, final long random_id, int ttl) {
        if (random_id == 0 || dialog_id == 0 || ttl <= 0) {
            return;
        }
        int lower_part = (int)dialog_id;
        int high_id = (int)(dialog_id >> 32);
        if (lower_part != 0) {
            return;
        }
        TLRPC.EncryptedChat chat = getEncryptedChat(high_id);
        if (chat == null) {
            return;
        }
        ArrayList<Long> random_ids = new ArrayList<Long>();
        random_ids.add(random_id);
        SecretChatHelper.getInstance().sendMessagesReadMessage(chat, random_ids, null);
        int time = ConnectionsManager.getInstance().getCurrentTime();
        MessagesStorage.getInstance().createTaskForSecretChat(chat.id, time, time, 0, random_ids);
    }

    public void markDialogAsRead(final long dialog_id, final int max_id, final int max_positive_id, final int offset, final int max_date, final boolean was, final boolean popup) {
        int lower_part = (int)dialog_id;
        int high_id = (int)(dialog_id >> 32);

        if (lower_part != 0) {
            if (max_positive_id == 0 && offset == 0 || high_id == 1) {
                return;
            }
            TLRPC.TL_messages_readHistory req = new TLRPC.TL_messages_readHistory();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = getUser(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.user_id = user.id;
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                    req.peer.user_id = user.id;
                }
            }
            req.max_id = max_positive_id;
            req.offset = offset;
            req.read_contents = true;
            if (offset == 0) {
                MessagesStorage.getInstance().processPendingRead(dialog_id, max_positive_id, max_date, false);
                MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                    @Override
                    public void run() {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                                if (dialog != null) {
                                    dialog.unread_count = 0;
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                                }
                                if (!popup) {
                                    NotificationsController.getInstance().processReadMessages(null, dialog_id, 0, max_positive_id, false);
                                    HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();
                                    dialogsToUpdate.put(dialog_id, 0);
                                    NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                                } else {
                                    NotificationsController.getInstance().processReadMessages(null, dialog_id, 0, max_positive_id, true);
                                    HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();
                                    dialogsToUpdate.put(dialog_id, -1);
                                    NotificationsController.getInstance().processDialogsUpdateRead(dialogsToUpdate);
                                }
                            }
                        });
                    }
                });
            }
            if (req.max_id != Integer.MAX_VALUE) {
                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            MessagesStorage.getInstance().processPendingRead(dialog_id, max_positive_id, max_date, true);
                            TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory) response;
                            if (res.offset > 0) {
                                markDialogAsRead(dialog_id, 0, max_positive_id, res.offset, max_date, was, popup);
                            }
                            processNewDifferenceParams(res.seq, res.pts, -1);
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

                ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
                            TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                            if (dialog != null) {
                                dialog.unread_count = 0;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_READ_DIALOG_MESSAGE);
                            }
                            HashMap<Long, Integer> dialogsToUpdate = new HashMap<Long, Integer>();
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

    public long createChat(String title, ArrayList<Integer> selectedContacts, final TLRPC.InputFile uploadedAvatar, boolean isBroadcast) {
        if (isBroadcast) {
            TLRPC.TL_chat chat = new TLRPC.TL_chat();
            chat.id = UserConfig.lastBroadcastId;
            chat.title = title;
            chat.photo = new TLRPC.TL_chatPhotoEmpty();
            chat.participants_count = selectedContacts.size();
            chat.date = (int)(System.currentTimeMillis() / 1000);
            chat.left = false;
            chat.version = 1;
            UserConfig.lastBroadcastId--;
            putChat(chat, false);
            ArrayList<TLRPC.Chat> chatsArrays = new ArrayList<TLRPC.Chat>();
            chatsArrays.add(chat);
            MessagesStorage.getInstance().putUsersAndChats(null, chatsArrays, true, true);

            TLRPC.TL_chatParticipants participants = new TLRPC.TL_chatParticipants();
            participants.chat_id = chat.id;
            participants.admin_id = UserConfig.getClientUserId();
            participants.version = 1;
            for (Integer id : selectedContacts) {
                TLRPC.TL_chatParticipant participant = new TLRPC.TL_chatParticipant();
                participant.user_id = id;
                participant.inviter_id = UserConfig.getClientUserId();
                participant.date = (int)(System.currentTimeMillis() / 1000);
                participants.participants.add(participant);
            }
            MessagesStorage.getInstance().updateChatInfo(chat.id, participants, false);

            TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();
            newMsg.action = new TLRPC.TL_messageActionCreatedBroadcastList();
            newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
            newMsg.from_id = UserConfig.getClientUserId();
            newMsg.dialog_id = AndroidUtilities.makeBroadcastId(chat.id);
            newMsg.to_id = new TLRPC.TL_peerChat();
            newMsg.to_id.chat_id = chat.id;
            newMsg.date = ConnectionsManager.getInstance().getCurrentTime();
            newMsg.random_id = 0;
            UserConfig.saveConfig(false);
            MessageObject newMsgObj = new MessageObject(newMsg, users);
            newMsgObj.messageOwner.send_state = MessageObject.MESSAGE_SEND_STATE_SENT;

            ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
            objArr.add(newMsgObj);
            ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
            arr.add(newMsg);
            MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
            updateInterfaceWithMessages(newMsg.dialog_id, objArr);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidCreated, chat.id);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);

            return 0;
        } else {
            TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
            req.title = title;
            for (Integer uid : selectedContacts) {
                TLRPC.User user = getUser(uid);
                if (user == null) {
                    continue;
                }
                req.users.add(getInputUser(user));
            }
            return ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidFailCreate);
                            }
                        });
                        return;
                    }
                    final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(res.users, false);
                            putChats(res.chats, false);
                            final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                            messagesObj.add(new MessageObject(res.message, users));
                            TLRPC.Chat chat = res.chats.get(0);
                            updateInterfaceWithMessages(-chat.id, messagesObj);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatDidCreated, chat.id);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            if (uploadedAvatar != null) {
                                changeChatAvatar(chat.id, uploadedAvatar);
                            }
                        }
                    });

                    final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                    messages.add(res.message);
                    MessagesStorage.getInstance().putMessages(messages, true, true, false, 0);
                    processNewDifferenceParams(res.seq, res.pts, -1);
                }
            });
        }
    }

    public void addUserToChat(int chat_id, final TLRPC.User user, final TLRPC.ChatParticipants info, int count_fwd) {
        if (user == null) {
            return;
        }

        if (chat_id > 0) {
            TLRPC.TL_messages_addChatUser req = new TLRPC.TL_messages_addChatUser();
            req.chat_id = chat_id;
            req.fwd_limit = count_fwd;
            req.user_id = getInputUser(user);

            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        return;
                    }

                    final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(res.users, false);
                            putChats(res.chats, false);
                            final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                            messagesObj.add(new MessageObject(res.message, users));
                            TLRPC.Chat chat = res.chats.get(0);
                            updateInterfaceWithMessages(-chat.id, messagesObj);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);

                            if (info != null) {
                                for (TLRPC.TL_chatParticipant p : info.participants) {
                                    if (p.user_id == user.id) {
                                        return;
                                    }
                                }
                                TLRPC.TL_chatParticipant newPart = new TLRPC.TL_chatParticipant();
                                newPart.user_id = user.id;
                                newPart.inviter_id = UserConfig.getClientUserId();
                                newPart.date = ConnectionsManager.getInstance().getCurrentTime();
                                info.participants.add(0, newPart);
                                MessagesStorage.getInstance().updateChatInfo(info.chat_id, info, true);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info.chat_id, info);
                            }
                        }
                    });

                    final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                    messages.add(res.message);
                    MessagesStorage.getInstance().putMessages(messages, true, true, false, 0);
                    processNewDifferenceParams(res.seq, res.pts, -1);
                }
            });
        } else {
            if (info != null) {
                for (TLRPC.TL_chatParticipant p : info.participants) {
                    if (p.user_id == user.id) {
                        return;
                    }
                }

                TLRPC.Chat chat = getChat(chat_id);
                chat.participants_count++;
                ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<TLRPC.Chat>();
                chatArrayList.add(chat);
                MessagesStorage.getInstance().putUsersAndChats(null, chatArrayList, true, true);

                TLRPC.TL_chatParticipant newPart = new TLRPC.TL_chatParticipant();
                newPart.user_id = user.id;
                newPart.inviter_id = UserConfig.getClientUserId();
                newPart.date = ConnectionsManager.getInstance().getCurrentTime();
                info.participants.add(0, newPart);
                MessagesStorage.getInstance().updateChatInfo(info.chat_id, info, true);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info.chat_id, info);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
            }
        }
    }

    public void deleteUserFromChat(final int chat_id, final TLRPC.User user, final TLRPC.ChatParticipants info) {
        if (user == null) {
            return;
        }
        if (chat_id > 0) {
            TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
            req.chat_id = chat_id;
            req.user_id = getInputUser(user);
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        return;
                    }
                    final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                    if (user.id == UserConfig.getClientUserId()) {
                        res.chats = null;
                    }
                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(res.users, false);
                            putChats(res.chats, false);
                            if (user.id != UserConfig.getClientUserId()) {
                                final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                                messagesObj.add(new MessageObject(res.message, users));
                                TLRPC.Chat chat = res.chats.get(0);
                                updateInterfaceWithMessages(-chat.id, messagesObj);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            }
                            boolean changed = false;
                            if (info != null) {
                                for (int a = 0; a < info.participants.size(); a++) {
                                    TLRPC.TL_chatParticipant p = info.participants.get(a);
                                    if (p.user_id == user.id) {
                                        info.participants.remove(a);
                                        changed = true;
                                        break;
                                    }
                                }
                                if (changed) {
                                    MessagesStorage.getInstance().updateChatInfo(chat_id, info, true);
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info.chat_id, info);
                                } else {
                                    MessagesStorage.getInstance().updateChatInfo(chat_id, user.id, true, 0, 0);
                                }
                            } else {
                                MessagesStorage.getInstance().updateChatInfo(chat_id, user.id, true, 0, 0);
                            }
                        }
                    });

                    if (user.id != UserConfig.getClientUserId()) {
                        final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                        messages.add(res.message);
                        MessagesStorage.getInstance().putMessages(messages, true, true, false, 0);
                    }
                    processNewDifferenceParams(res.seq, res.pts, -1);
                }
            });
        } else {
            if (info != null) {
                TLRPC.Chat chat = getChat(chat_id);
                chat.participants_count--;
                ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<TLRPC.Chat>();
                chatArrayList.add(chat);
                MessagesStorage.getInstance().putUsersAndChats(null, chatArrayList, true, true);

                boolean changed = false;
                if (info != null) {
                    for (int a = 0; a < info.participants.size(); a++) {
                        TLRPC.TL_chatParticipant p = info.participants.get(a);
                        if (p.user_id == user.id) {
                            info.participants.remove(a);
                            changed = true;
                            break;
                        }
                    }
                    if (changed) {
                        MessagesStorage.getInstance().updateChatInfo(info.chat_id, info, true);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info.chat_id, info);
                    }
                }
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_MEMBERS);
            }
        }
    }

    public void changeChatTitle(int chat_id, String title) {
        if (chat_id > 0) {
            TLRPC.TL_messages_editChatTitle req = new TLRPC.TL_messages_editChatTitle();
            req.chat_id = chat_id;
            req.title = title;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        return;
                    }
                    final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                    MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            putUsers(res.users, false);
                            putChats(res.chats, false);
                            final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                            messagesObj.add(new MessageObject(res.message, users));
                            TLRPC.Chat chat = res.chats.get(0);
                            updateInterfaceWithMessages(-chat.id, messagesObj);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_NAME);
                        }
                    });

                    final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                    messages.add(res.message);
                    MessagesStorage.getInstance().putMessages(messages, true, true, false, 0);
                    processNewDifferenceParams(res.seq, res.pts, -1);
                }
            });
        } else {
            TLRPC.Chat chat = getChat(chat_id);
            chat.title = title;
            ArrayList<TLRPC.Chat> chatArrayList = new ArrayList<TLRPC.Chat>();
            chatArrayList.add(chat);
            MessagesStorage.getInstance().putUsersAndChats(null, chatArrayList, true, true);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_NAME);
        }
    }

    public void changeChatAvatar(int chat_id, TLRPC.InputFile uploadedAvatar) {
        TLRPC.TL_messages_editChatPhoto req2 = new TLRPC.TL_messages_editChatPhoto();
        req2.chat_id = chat_id;
        if (uploadedAvatar != null) {
            req2.photo = new TLRPC.TL_inputChatUploadedPhoto();
            req2.photo.file = uploadedAvatar;
            req2.photo.crop = new TLRPC.TL_inputPhotoCropAuto();
        } else {
            req2.photo = new TLRPC.TL_inputChatPhotoEmpty();
        }
        ConnectionsManager.getInstance().performRpc(req2, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage) response;
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);

                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        putUsers(res.users, false);
                        putChats(res.chats, false);
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_CHAT_AVATAR);
                    }
                });

                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.getInstance().putMessages(messages, true, true, false, 0);
                processNewDifferenceParams(res.seq, res.pts, -1);
            }
        });
    }

    public void unregistedPush() {
        if (UserConfig.registeredForPush && UserConfig.pushString.length() == 0) {
            TLRPC.TL_account_unregisterDevice req = new TLRPC.TL_account_unregisterDevice();
            req.token = UserConfig.pushString;
            req.token_type = 2;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
    }

    public void logOut() {
        TLRPC.TL_auth_logOut req = new TLRPC.TL_auth_logOut();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                ConnectionsManager.getInstance().cleanUp();
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
            req.lang_code = LocaleController.getLocaleString(Locale.getDefault());
            req.device_model = Build.MANUFACTURER + Build.MODEL;
            if (req.device_model == null) {
                req.device_model = "Android unknown";
            }
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            req.app_version = pInfo.versionName + " (" + pInfo.versionCode + ")";
            if (req.app_version == null) {
                req.app_version = "App version unknown";
            }

        } catch (Exception e) {
            FileLog.e("tmessages", e);
            req.lang_code = "en";
            req.device_model = "Android unknown";
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            req.app_version = "App version unknown";
        }

        if (req.lang_code == null || req.lang_code.length() == 0) {
            req.lang_code = "en";
        }
        if (req.device_model == null || req.device_model.length() == 0) {
            req.device_model = "Android unknown";
        }
        if (req.app_version == null || req.app_version.length() == 0) {
            req.app_version = "App version unknown";
        }
        if (req.system_version == null || req.system_version.length() == 0) {
            req.system_version = "SDK Unknown";
        }

        if (req.app_version != null) {
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
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
    }

    public void loadCurrentState() {
        if (updatingState) {
            return;
        }
        updatingState = true;
        TLRPC.TL_updates_getState req = new TLRPC.TL_updates_getState();
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                updatingState = false;
                if (error == null) {
                    TLRPC.TL_updates_state res = (TLRPC.TL_updates_state) response;
                    MessagesStorage.lastDateValue = res.date;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastQtsValue = res.qts;
                    processUpdatesQueue(2);
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

    private void processUpdatesQueue(int state) {
        if (!updatesQueue.isEmpty()) {
            Collections.sort(updatesQueue, new Comparator<TLRPC.Updates>() {
                @Override
                public int compare(TLRPC.Updates updates, TLRPC.Updates updates2) {
                    int seq1 = getUpdateSeq(updates);
                    int seq2 = getUpdateSeq(updates2);
                    if (seq1 == seq2) {
                        return 0;
                    } else if (seq1 > seq2) {
                        return 1;
                    }
                    return -1;
                }
            });
            boolean anyProceed = false;
            if (state == 2) {
                TLRPC.Updates updates = updatesQueue.get(0);
                MessagesStorage.lastSeqValue = getUpdateSeq(updates);
            }
            for (int a = 0; a < updatesQueue.size(); a++) {
                TLRPC.Updates updates = updatesQueue.get(a);
                int seq = getUpdateSeq(updates);
                if (MessagesStorage.lastSeqValue + 1 == seq || MessagesStorage.lastSeqValue == seq) {
                    processUpdates(updates, true);
                    anyProceed = true;
                    updatesQueue.remove(a);
                    a--;
                } else if (MessagesStorage.lastSeqValue < seq) {
                    if (updatesStartWaitTime != 0 && (anyProceed || updatesStartWaitTime + 1500 > System.currentTimeMillis())) {
                        FileLog.e("tmessages", "HOLE IN UPDATES QUEUE - will wait more time");
                        if (anyProceed) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        return;
                    } else {
                        FileLog.e("tmessages", "HOLE IN UPDATES QUEUE - getDifference");
                        updatesStartWaitTime = 0;
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
            updatesStartWaitTime = 0;
            if (state == 1) {
                final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState, stateCopy);
                    }
                });
            }
        } else {
            if (state == 1) {
                final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState, stateCopy);
                    }
                });
            } else {
                updatesStartWaitTime = 0;
            }
        }
    }

    public void getDifference() {
        registerForPush(UserConfig.pushString);
        if (MessagesStorage.lastDateValue == 0) {
            loadCurrentState();
            return;
        }
        if (gettingDifference) {
            return;
        }
        if (!firstGettingTask) {
            getNewDeleteTask(null);
            firstGettingTask = true;
        }
        gettingDifference = true;
        TLRPC.TL_updates_getDifference req = new TLRPC.TL_updates_getDifference();
        req.pts = MessagesStorage.lastPtsValue;
        req.date = MessagesStorage.lastDateValue;
        req.qts = MessagesStorage.lastQtsValue;
        FileLog.e("tmessages", "start getDifference with date = " + MessagesStorage.lastDateValue + " pts = " + MessagesStorage.lastPtsValue + " seq = " + MessagesStorage.lastSeqValue);
        if (ConnectionsManager.getInstance().getConnectionState() == 0) {
            ConnectionsManager.getInstance().setConnectionState(3);
            final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState, stateCopy);
                }
            });
        }
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                gettingDifferenceAgain = false;
                if (error == null) {
                    final TLRPC.updates_Difference res = (TLRPC.updates_Difference) response;
                    gettingDifferenceAgain = res instanceof TLRPC.TL_updates_differenceSlice;

                    final HashMap<Integer, TLRPC.User> usersDict = new HashMap<Integer, TLRPC.User>();
                    for (TLRPC.User user : res.users) {
                        usersDict.put(user.id, user);
                    }

                    final ArrayList<TLRPC.TL_updateMessageID> msgUpdates = new ArrayList<TLRPC.TL_updateMessageID>();
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
                            if (!msgUpdates.isEmpty()) {
                                final HashMap<Integer, Integer> corrected = new HashMap<Integer, Integer>();
                                for (TLRPC.TL_updateMessageID update : msgUpdates) {
                                    Integer oldId = MessagesStorage.getInstance().updateMessageStateAndId(update.random_id, null, update.id, 0, false);
                                    if (oldId != null) {
                                        corrected.put(oldId, update.id);
                                    }
                                }

                                if (!corrected.isEmpty()) {
                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            for (HashMap.Entry<Integer, Integer> entry : corrected.entrySet()) {
                                                Integer oldId = entry.getKey();
                                                SendMessagesHelper.getInstance().processSentMessage(oldId);
                                                Integer newId = entry.getValue();
                                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messageReceivedByServer, oldId, newId, null);
                                            }
                                        }
                                    });
                                }
                            }

                            Utilities.stageQueue.postRunnable(new Runnable() {
                                @Override
                                public void run() {
                                    if (!res.new_messages.isEmpty() || !res.new_encrypted_messages.isEmpty()) {
                                        final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<Long, ArrayList<MessageObject>>();
                                        for (TLRPC.EncryptedMessage encryptedMessage : res.new_encrypted_messages) {
                                            ArrayList<TLRPC.Message> decryptedMessages = SecretChatHelper.getInstance().decryptMessage(encryptedMessage);
                                            if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                                                for (TLRPC.Message message : decryptedMessages) {
                                                    res.new_messages.add(message);
                                                }
                                            }
                                        }

                                        final ArrayList<MessageObject> pushMessages = new ArrayList<MessageObject>();
                                        for (TLRPC.Message message : res.new_messages) {
                                            MessageObject obj = new MessageObject(message, usersDict, 2);

                                            long dialog_id = obj.messageOwner.dialog_id;
                                            if (dialog_id == 0) {
                                                if (obj.messageOwner.to_id.chat_id != 0) {
                                                    dialog_id = -obj.messageOwner.to_id.chat_id;
                                                } else {
                                                    dialog_id = obj.messageOwner.to_id.user_id;
                                                }
                                            }

                                            if (!obj.isFromMe() && obj.isUnread()) {
                                                pushMessages.add(obj);
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
                                            ArrayList<MessageObject> arr = messages.get(uid);
                                            if (arr == null) {
                                                arr = new ArrayList<MessageObject>();
                                                messages.put(uid, arr);
                                            }
                                            arr.add(obj);
                                        }

                                        SecretChatHelper.getInstance().processPendingEncMessages();

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
                                                MessagesStorage.getInstance().startTransaction(false);
                                                MessagesStorage.getInstance().putMessages(res.new_messages, false, false, false, MediaController.getInstance().getAutodownloadMask());
                                                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, false, false);
                                                MessagesStorage.getInstance().commitTransaction(false);
                                            }
                                        });
                                    }

                                    if (res != null && !res.other_updates.isEmpty()) {
                                        processUpdateArray(res.other_updates, res.users, res.chats);
                                    }

                                    gettingDifference = false;
                                    if (res instanceof TLRPC.TL_updates_difference) {
                                        MessagesStorage.lastSeqValue = res.state.seq;
                                        MessagesStorage.lastDateValue = res.state.date;
                                        MessagesStorage.lastPtsValue = res.state.pts;
                                        MessagesStorage.lastQtsValue = res.state.qts;
                                        ConnectionsManager.getInstance().setConnectionState(0);
                                        processUpdatesQueue(1);
                                    } else if (res instanceof TLRPC.TL_updates_differenceSlice) {
                                        MessagesStorage.lastDateValue = res.intermediate_state.date;
                                        MessagesStorage.lastPtsValue = res.intermediate_state.pts;
                                        MessagesStorage.lastQtsValue = res.intermediate_state.qts;
                                        gettingDifferenceAgain = true;
                                        getDifference();
                                    } else if (res instanceof TLRPC.TL_updates_differenceEmpty) {
                                        MessagesStorage.lastSeqValue = res.seq;
                                        MessagesStorage.lastDateValue = res.date;
                                        ConnectionsManager.getInstance().setConnectionState(0);
                                        processUpdatesQueue(1);
                                    }
                                    MessagesStorage.getInstance().saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                                    FileLog.e("tmessages", "received difference with date = " + MessagesStorage.lastDateValue + " pts = " + MessagesStorage.lastPtsValue + " seq = " + MessagesStorage.lastSeqValue);
                                    FileLog.e("tmessages", "messages = " + res.new_messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                                }
                            });
                        }
                    });
                } else {
                    gettingDifference = false;
                    ConnectionsManager.getInstance().setConnectionState(0);
                    final int stateCopy = ConnectionsManager.getInstance().getConnectionState();
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.didUpdatedConnectionState, stateCopy);
                        }
                    });
                }
            }
        });
    }

    public void processUpdates(final TLRPC.Updates updates, boolean fromQueue) {
        boolean needGetDiff = false;
        boolean needReceivedQueue = false;
        boolean addedToQueue = false;
        boolean updateStatus = false;
        if (updates instanceof TLRPC.TL_updateShort) {
            ArrayList<TLRPC.Update> arr = new ArrayList<TLRPC.Update>();
            arr.add(updates.update);
            processUpdateArray(arr, null, null);
        } else if (updates instanceof TLRPC.TL_updateShortChatMessage) {
            TLRPC.User user = getUser(updates.from_id);
            if (user != null && user.status != null && user.status.expires <= 0) {
                onlinePrivacy.put(user.id, ConnectionsManager.getInstance().getCurrentTime());
                updateStatus = true;
            }
            boolean missingData = getChat(updates.chat_id) == null || user == null;
            if (missingData) {
                needGetDiff = true;
            } else {
                if (MessagesStorage.lastSeqValue + 1 == updates.seq) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.from_id = updates.from_id;
                    message.id = updates.id;
                    message.to_id = new TLRPC.TL_peerChat();
                    message.to_id.chat_id = updates.chat_id;
                    message.message = updates.message;
                    message.date = updates.date;
                    message.flags = TLRPC.MESSAGE_FLAG_UNREAD;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    MessagesStorage.lastSeqValue = updates.seq;
                    MessagesStorage.lastPtsValue = updates.pts;
                    final MessageObject obj = new MessageObject(message, null);
                    final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
                    objArr.add(obj);
                    ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                    arr.add(message);
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
                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!obj.isFromMe() && obj.isUnread()) {
                                        NotificationsController.getInstance().processNewMessages(objArr, true);
                                    }
                                }
                            });
                        }
                    });
                    MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
                } else if (MessagesStorage.lastSeqValue != updates.seq) {
                    FileLog.e("tmessages", "need get diff TL_updateShortChatMessage, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_updateShortChatMessage to queue");
                        updatesQueue.add(updates);
                        addedToQueue = true;
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updateShortMessage) {
            TLRPC.User user = getUser(updates.from_id);
            if (user != null && user.status != null && user.status.expires <= 0) {
                onlinePrivacy.put(user.id, ConnectionsManager.getInstance().getCurrentTime());
                updateStatus = true;
            }
            boolean missingData = user == null;
            if (missingData) {
                needGetDiff = true;
            } else {
                if (MessagesStorage.lastSeqValue + 1 == updates.seq) {
                    TLRPC.TL_message message = new TLRPC.TL_message();
                    message.from_id = updates.from_id;
                    message.id = updates.id;
                    message.to_id = new TLRPC.TL_peerUser();
                    message.to_id.user_id = updates.from_id;
                    message.message = updates.message;
                    message.date = updates.date;
                    message.flags = TLRPC.MESSAGE_FLAG_UNREAD;
                    message.media = new TLRPC.TL_messageMediaEmpty();
                    MessagesStorage.lastSeqValue = updates.seq;
                    MessagesStorage.lastPtsValue = updates.pts;
                    MessagesStorage.lastDateValue = updates.date;
                    final MessageObject obj = new MessageObject(message, null);
                    final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
                    objArr.add(obj);
                    ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                    arr.add(message);
                    final boolean printUpdate = updatePrintingUsersWithNewMessages(updates.from_id, objArr);
                    if (printUpdate) {
                        updatePrintingStrings();
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (printUpdate) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, UPDATE_MASK_USER_PRINT);
                            }
                            updateInterfaceWithMessages(updates.from_id, objArr);
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.dialogsNeedReload);
                        }
                    });
                    MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!obj.isFromMe() && obj.isUnread()) {
                                        NotificationsController.getInstance().processNewMessages(objArr, true);
                                    }
                                }
                            });
                        }
                    });
                    MessagesStorage.getInstance().putMessages(arr, false, true, false, 0);
                } else if (MessagesStorage.lastSeqValue != updates.seq) {
                    FileLog.e("tmessages", "need get diff TL_updateShortMessage, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                    if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                        if (updatesStartWaitTime == 0) {
                            updatesStartWaitTime = System.currentTimeMillis();
                        }
                        FileLog.e("tmessages", "add TL_updateShortMessage to queue");
                        updatesQueue.add(updates);
                        addedToQueue = true;
                    } else {
                        needGetDiff = true;
                    }
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesCombined) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq_start || MessagesStorage.lastSeqValue == updates.seq_start) {
                MessagesStorage.getInstance().putUsersAndChats(updates.users, updates.chats, true, true);
                int lastPtsValue = MessagesStorage.lastPtsValue;
                int lastQtsValue = MessagesStorage.lastQtsValue;
                if (!processUpdateArray(updates.updates, updates.users, updates.chats)) {
                    MessagesStorage.lastPtsValue = lastPtsValue;
                    MessagesStorage.lastQtsValue = lastQtsValue;
                    FileLog.e("tmessages", "need get diff inner TL_updatesCombined, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                    needGetDiff = true;
                } else {
                    MessagesStorage.lastDateValue = updates.date;
                    MessagesStorage.lastSeqValue = updates.seq;
                    if (MessagesStorage.lastQtsValue != lastQtsValue) {
                        needReceivedQueue = true;
                    }
                }
            } else {
                FileLog.e("tmessages", "need get diff TL_updatesCombined, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq_start);
                if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                    if (updatesStartWaitTime == 0) {
                        updatesStartWaitTime = System.currentTimeMillis();
                    }
                    FileLog.e("tmessages", "add TL_updatesCombined to queue");
                    updatesQueue.add(updates);
                    addedToQueue = true;
                } else {
                    needGetDiff = true;
                }
            }
        } else if (updates instanceof TLRPC.TL_updates) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq || updates.seq == 0 || updates.seq == MessagesStorage.lastSeqValue) {
                MessagesStorage.getInstance().putUsersAndChats(updates.users, updates.chats, true, true);
                int lastPtsValue = MessagesStorage.lastPtsValue;
                int lastQtsValue = MessagesStorage.lastQtsValue;
                if (!processUpdateArray(updates.updates, updates.users, updates.chats)) {
                    needGetDiff = true;
                    MessagesStorage.lastPtsValue = lastPtsValue;
                    MessagesStorage.lastQtsValue = lastQtsValue;
                    FileLog.e("tmessages", "need get diff inner TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                } else {
                    MessagesStorage.lastDateValue = updates.date;
                    if (updates.seq != 0) {
                        MessagesStorage.lastSeqValue = updates.seq;
                    }
                    if (MessagesStorage.lastQtsValue != lastQtsValue) {
                        needReceivedQueue = true;
                    }
                }
            } else {
                FileLog.e("tmessages", "need get diff TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                if (gettingDifference || updatesStartWaitTime == 0 || updatesStartWaitTime != 0 && updatesStartWaitTime + 1500 > System.currentTimeMillis()) {
                    if (updatesStartWaitTime == 0) {
                        updatesStartWaitTime = System.currentTimeMillis();
                    }
                    FileLog.e("tmessages", "add TL_updates to queue");
                    updatesQueue.add(updates);
                    addedToQueue = true;
                } else {
                    needGetDiff = true;
                }
            }
        } else if (updates instanceof TLRPC.TL_updatesTooLong) {
            FileLog.e("tmessages", "need get diff TL_updatesTooLong");
            needGetDiff = true;
        } else if (updates instanceof UserActionUpdates) {
            MessagesStorage.lastSeqValue = updates.seq;
        }
        if (needGetDiff && !fromQueue) {
            getDifference();
        } else if (!fromQueue && !updatesQueue.isEmpty()) {
            processUpdatesQueue(0);
        }
        if (needReceivedQueue) {
            TLRPC.TL_messages_receivedQueue req = new TLRPC.TL_messages_receivedQueue();
            req.max_qts = MessagesStorage.lastQtsValue;
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
            return true;
        }
        long currentTime = System.currentTimeMillis();

        final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<Long, ArrayList<MessageObject>>();
        final ArrayList<MessageObject> pushMessages = new ArrayList<MessageObject>();
        final ArrayList<TLRPC.Message> messagesArr = new ArrayList<TLRPC.Message>();
        final ArrayList<Integer> markAsReadMessages = new ArrayList<Integer>();
        final HashMap<Integer, Integer> markAsReadEncrypted = new HashMap<Integer, Integer>();
        final ArrayList<Integer> deletedMessages = new ArrayList<Integer>();
        boolean printChanged = false;
        final ArrayList<TLRPC.ChatParticipants> chatInfoToUpdate = new ArrayList<TLRPC.ChatParticipants>();
        final ArrayList<TLRPC.Update> updatesOnMainThread = new ArrayList<TLRPC.Update>();
        final ArrayList<TLRPC.TL_updateEncryptedMessagesRead> tasks = new ArrayList<TLRPC.TL_updateEncryptedMessagesRead>();
        final ArrayList<Integer> contactsIds = new ArrayList<Integer>();

        boolean checkForUsers = true;
        ConcurrentHashMap<Integer, TLRPC.User> usersDict;
        ConcurrentHashMap<Integer, TLRPC.Chat> chatsDict;
        if (usersArr != null) {
            usersDict = new ConcurrentHashMap<Integer, TLRPC.User>();
            for (TLRPC.User user : usersArr) {
                usersDict.put(user.id, user);
            }
        } else {
            checkForUsers = false;
            usersDict = users;
        }
        if (chatsArr != null) {
            chatsDict = new ConcurrentHashMap<Integer, TLRPC.Chat>();
            for (TLRPC.Chat chat : chatsArr) {
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

        for (TLRPC.Update update : updates) {
            if (update instanceof TLRPC.TL_updateNewMessage) {
                TLRPC.TL_updateNewMessage upd = (TLRPC.TL_updateNewMessage)update;
                if (checkForUsers) {
                    TLRPC.User user = getUser(upd.message.from_id);
                    if (usersDict.get(upd.message.from_id) == null && user == null || upd.message.to_id.chat_id != 0 && chatsDict.get(upd.message.to_id.chat_id) == null && getChat(upd.message.to_id.chat_id) == null) {
                        return false;
                    }

                    if (user != null && user.status != null && user.status.expires <= 0) {
                        onlinePrivacy.put(upd.message.from_id, ConnectionsManager.getInstance().getCurrentTime());
                        interfaceUpdateMask |= UPDATE_MASK_STATUS;
                    }
                }
                messagesArr.add(upd.message);
                MessageObject obj = new MessageObject(upd.message, usersDict, 2);
                if (obj.type == 11) {
                    interfaceUpdateMask |= UPDATE_MASK_CHAT_AVATAR;
                } else if (obj.type == 10) {
                    interfaceUpdateMask |= UPDATE_MASK_CHAT_NAME;
                }
                long uid;
                if (upd.message.to_id.chat_id != 0) {
                    uid = -upd.message.to_id.chat_id;
                } else {
                    if (upd.message.to_id.user_id == UserConfig.getClientUserId()) {
                        upd.message.to_id.user_id = upd.message.from_id;
                    }
                    uid = upd.message.to_id.user_id;
                }
                ArrayList<MessageObject> arr = messages.get(uid);
                if (arr == null) {
                    arr = new ArrayList<MessageObject>();
                    messages.put(uid, arr);
                }
                arr.add(obj);
                MessagesStorage.lastPtsValue = update.pts;
                if (!obj.isFromMe() && obj.isUnread()) {
                    pushMessages.add(obj);
                }
            } else if (update instanceof TLRPC.TL_updateReadMessages) {
                markAsReadMessages.addAll(update.messages);
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateDeleteMessages) {
                deletedMessages.addAll(update.messages);
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateRestoreMessages) {
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateUserTyping || update instanceof TLRPC.TL_updateChatUserTyping) {
                if (update.action instanceof TLRPC.TL_sendMessageTypingAction && update.user_id != UserConfig.getClientUserId()) {
                    long uid = -update.chat_id;
                    if (uid == 0) {
                        uid = update.user_id;
                    }
                    ArrayList<PrintingUser> arr = printingUsers.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<PrintingUser>();
                        printingUsers.put(uid, arr);
                    }
                    boolean exist = false;
                    for (PrintingUser u : arr) {
                        if (u.userId == update.user_id) {
                            exist = true;
                            u.lastTime = currentTime;
                            break;
                        }
                    }
                    if (!exist) {
                        PrintingUser newUser = new PrintingUser();
                        newUser.userId = update.user_id;
                        newUser.lastTime = currentTime;
                        arr.add(newUser);
                        printChanged = true;
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
                if (enableJoined && usersDict.containsKey(update.user_id)) {
                    TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                    newMessage.action = new TLRPC.TL_messageActionUserJoined();
                    newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                    UserConfig.saveConfig(false);
                    newMessage.flags = TLRPC.MESSAGE_FLAG_UNREAD;
                    newMessage.date = update.date;
                    newMessage.from_id = update.user_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.getClientUserId();
                    newMessage.dialog_id = update.user_id;

                    messagesArr.add(newMessage);
                    MessageObject obj = new MessageObject(newMessage, usersDict);
                    ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                    if (arr == null) {
                        arr = new ArrayList<MessageObject>();
                        messages.put(newMessage.dialog_id, arr);
                    }
                    arr.add(obj);
                    pushMessages.add(obj);
                }
            } else if (update instanceof TLRPC.TL_updateContactLink) {
                if (update.my_link instanceof TLRPC.TL_contacts_myLinkContact || update.my_link instanceof TLRPC.TL_contacts_myLinkRequested && update.my_link.contact) {
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
            } else if (update instanceof TLRPC.TL_updateActivation) {
                //DEPRECATED
            } else if (update instanceof TLRPC.TL_updateNewAuthorization) {
                TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                newMessage.action = new TLRPC.TL_messageActionLoginUnknownLocation();
                newMessage.action.title = update.device;
                newMessage.action.address = update.location;
                newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                UserConfig.saveConfig(false);
                newMessage.flags = TLRPC.MESSAGE_FLAG_UNREAD;
                newMessage.date = update.date;
                newMessage.from_id = 777000;
                newMessage.to_id = new TLRPC.TL_peerUser();
                newMessage.to_id.user_id = UserConfig.getClientUserId();
                newMessage.dialog_id = 777000;

                messagesArr.add(newMessage);
                MessageObject obj = new MessageObject(newMessage, usersDict);
                ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<MessageObject>();
                    messages.put(newMessage.dialog_id, arr);
                }
                arr.add(obj);
                pushMessages.add(obj);
            } else if (update instanceof TLRPC.TL_updateNewGeoChatMessage) {
                //DEPRECATED
            } else if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
                MessagesStorage.lastQtsValue = update.qts;
                ArrayList<TLRPC.Message> decryptedMessages = SecretChatHelper.getInstance().decryptMessage(((TLRPC.TL_updateNewEncryptedMessage)update).message);
                if (decryptedMessages != null && !decryptedMessages.isEmpty()) {
                    int cid = ((TLRPC.TL_updateNewEncryptedMessage)update).message.chat_id;
                    long uid = ((long) cid) << 32;
                    ArrayList<MessageObject> arr = messages.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<MessageObject>();
                        messages.put(uid, arr);
                    }
                    for (TLRPC.Message message : decryptedMessages) {
                        messagesArr.add(message);
                        MessageObject obj = new MessageObject(message, usersDict, 2);
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
                        arr = new ArrayList<PrintingUser>();
                        printingUsers.put(uid, arr);
                    }
                    boolean exist = false;
                    for (PrintingUser u : arr) {
                        if (u.userId == update.user_id) {
                            exist = true;
                            u.lastTime = currentTime;
                            break;
                        }
                    }
                    if (!exist) {
                        PrintingUser newUser = new PrintingUser();
                        newUser.userId = update.user_id;
                        newUser.lastTime = currentTime;
                        arr.add(newUser);
                        printChanged = true;
                    }
                    onlinePrivacy.put(update.user_id, ConnectionsManager.getInstance().getCurrentTime());
                }
            } else if (update instanceof TLRPC.TL_updateEncryptedMessagesRead) {
                markAsReadEncrypted.put(update.chat_id, Math.max(update.max_date, update.date));
                tasks.add((TLRPC.TL_updateEncryptedMessagesRead)update);
            } else if (update instanceof TLRPC.TL_updateChatParticipantAdd) {
                MessagesStorage.getInstance().updateChatInfo(update.chat_id, update.user_id, false, update.inviter_id, update.version);
            } else if (update instanceof TLRPC.TL_updateChatParticipantDelete) {
                MessagesStorage.getInstance().updateChatInfo(update.chat_id, update.user_id, true, 0, update.version);
            } else if (update instanceof TLRPC.TL_updateDcOptions) {
                ConnectionsManager.getInstance().updateDcSettings(0);
            } else if (update instanceof TLRPC.TL_updateEncryption) {
                SecretChatHelper.getInstance().processUpdateEncryption((TLRPC.TL_updateEncryption) update, usersDict);
            } else if (update instanceof TLRPC.TL_updateUserBlocked) {
                final TLRPC.TL_updateUserBlocked finalUpdate = (TLRPC.TL_updateUserBlocked)update;
                if (finalUpdate.blocked) {
                    ArrayList<Integer> ids = new ArrayList<Integer>();
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
                TLRPC.TL_message newMessage = new TLRPC.TL_message();
                newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                UserConfig.saveConfig(false);
                newMessage.flags = TLRPC.MESSAGE_FLAG_UNREAD;
                newMessage.date = update.date;
                newMessage.from_id = 777000;
                newMessage.to_id = new TLRPC.TL_peerUser();
                newMessage.to_id.user_id = UserConfig.getClientUserId();
                newMessage.dialog_id = 777000;
                newMessage.media = update.media;
                newMessage.message = ((TLRPC.TL_updateServiceNotification)update).message;

                messagesArr.add(newMessage);
                MessageObject obj = new MessageObject(newMessage, usersDict);
                ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<MessageObject>();
                    messages.put(newMessage.dialog_id, arr);
                }
                arr.add(obj);
                pushMessages.add(obj);
            } else if (update instanceof TLRPC.TL_updatePrivacy) {
                updatesOnMainThread.add(update);
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

        SecretChatHelper.getInstance().processPendingEncMessages();

        if (!contactsIds.isEmpty()) {
            ContactsController.getInstance().processContactsUpdates(contactsIds, usersDict);
        }

        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (!pushMessages.isEmpty()) {
                            NotificationsController.getInstance().processNewMessages(pushMessages, true);
                        }
                    }
                });
            }
        });

        if (!messagesArr.isEmpty()) {
            MessagesStorage.getInstance().putMessages(messagesArr, true, true, false, MediaController.getInstance().getAutodownloadMask());
        }

        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                int updateMask = interfaceUpdateMaskFinal;

                boolean avatarsUpdate = false;
                if (!updatesOnMainThread.isEmpty()) {
                    ArrayList<TLRPC.User> dbUsers = new ArrayList<TLRPC.User>();
                    ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<TLRPC.User>();
                    SharedPreferences.Editor editor = null;
                    for (TLRPC.Update update : updatesOnMainThread) {
                        final TLRPC.User toDbUser = new TLRPC.User();
                        toDbUser.id = update.user_id;
                        final TLRPC.User currentUser = getUser(update.user_id);
                        if (update instanceof TLRPC.TL_updatePrivacy) {
                            if (update.key instanceof TLRPC.TL_privacyKeyStatusTimestamp) {
                                ContactsController.getInstance().setPrivacyRules(update.rules);
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
                        } else if (update instanceof TLRPC.TL_updateUserName) {
                            if (currentUser != null) {
                                currentUser.first_name = update.first_name;
                                currentUser.last_name = update.last_name;
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
                            avatarsUpdate = true;
                            toDbUser.photo = update.photo;
                            dbUsers.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateUserPhone) {
                            if (currentUser != null) {
                                currentUser.phone = update.phone;
                                Utilities.photoBookQueue.postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        ContactsController.getInstance().addContactToPhoneBook(currentUser, true);
                                    }
                                });
                            }
                            toDbUser.phone = update.phone;
                            dbUsers.add(toDbUser);
                        } else if (update instanceof TLRPC.TL_updateNotifySettings) {
                            if (update.notify_settings instanceof TLRPC.TL_peerNotifySettings && update.peer instanceof TLRPC.TL_notifyPeer) {
                                if (editor == null) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    editor = preferences.edit();
                                }
                                int dialog_id = update.peer.peer.user_id;
                                if (dialog_id == 0) {
                                    dialog_id = -update.peer.peer.chat_id;
                                }
                                if (update.notify_settings.mute_until != 0) {
                                    editor.putInt("notify2_" + dialog_id, 2);
                                } else {
                                    editor.remove("notify2_" + dialog_id);
                                }
                            }/* else if (update.peer instanceof TLRPC.TL_notifyChats) { disable global settings sync
                                if (editor == null) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    editor = preferences.edit();
                                }
                                editor.putBoolean("EnableGroup", update.notify_settings.mute_until == 0);
                                editor.putBoolean("EnablePreviewGroup", update.notify_settings.show_previews);
                            } else if (update.peer instanceof TLRPC.TL_notifyUsers) {
                                if (editor == null) {
                                    SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                    editor = preferences.edit();
                                }
                                editor.putBoolean("EnableAll", update.notify_settings.mute_until == 0);
                                editor.putBoolean("EnablePreviewAll", update.notify_settings.show_previews);
                            }*/
                        }
                    }
                    if (editor != null) {
                        editor.commit();
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.notificationsSettingsUpdated);
                    }
                    MessagesStorage.getInstance().updateUsers(dbUsersStatus, true, true, true);
                    MessagesStorage.getInstance().updateUsers(dbUsers, false, true, true);
                }

                if (!messages.isEmpty()) {
                    for (HashMap.Entry<Long, ArrayList<MessageObject>> entry : messages.entrySet()) {
                        Long key = entry.getKey();
                        ArrayList<MessageObject> value = entry.getValue();
                        updateInterfaceWithMessages(key, value);
                    }
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
                    for (TLRPC.ChatParticipants info : chatInfoToUpdate) {
                        MessagesStorage.getInstance().updateChatInfo(info.chat_id, info, true);
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.chatInfoDidLoaded, info.chat_id, info);
                    }
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
                        if (!markAsReadMessages.isEmpty()) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesRead, markAsReadMessages);
                            NotificationsController.getInstance().processReadMessages(markAsReadMessages, 0, 0, 0, false);

                            for (Integer id : markAsReadMessages) {
                                MessageObject obj = dialogMessage.get(id);
                                if (obj != null) {
                                    obj.setIsRead();
                                    updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                                }
                            }
                        }
                        if (!markAsReadEncrypted.isEmpty()) {
                            for (HashMap.Entry<Integer, Integer> entry : markAsReadEncrypted.entrySet()) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesReadedEncrypted, entry.getKey(), entry.getValue());
                                long dialog_id = (long) (entry.getKey()) << 32;
                                TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                                if (dialog != null) {
                                    MessageObject message = dialogMessage.get(dialog.top_message);
                                    if (message != null && message.messageOwner.date <= entry.getValue()) {
                                        message.setIsRead();
                                        updateMask |= UPDATE_MASK_READ_DIALOG_MESSAGE;
                                    }
                                }
                            }
                        }
                        if (!deletedMessages.isEmpty()) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.messagesDeleted, deletedMessages);
                            for (Integer id : deletedMessages) {
                                MessageObject obj = dialogMessage.get(id);
                                if (obj != null) {
                                    obj.deleted = true;
                                }
                            }
                        }
                        if (updateMask != 0) {
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.updateInterfaces, updateMask);
                        }
                    }
                });
            }
        });

        if (!markAsReadMessages.isEmpty() || !markAsReadEncrypted.isEmpty()) {
            if (!markAsReadMessages.isEmpty()) {
                MessagesStorage.getInstance().updateDialogsWithReadedMessages(markAsReadMessages, true);
            }
            MessagesStorage.getInstance().markMessagesAsRead(markAsReadMessages, markAsReadEncrypted, true);
        }
        if (!deletedMessages.isEmpty()) {
            MessagesStorage.getInstance().markMessagesAsDeleted(deletedMessages, true);
        }
        if (!deletedMessages.isEmpty()) {
            MessagesStorage.getInstance().updateDialogsWithDeletedMessages(deletedMessages, true);
        }
        if (!tasks.isEmpty()) {
            for (TLRPC.TL_updateEncryptedMessagesRead update : tasks) {
                MessagesStorage.getInstance().createTaskForSecretChat(update.chat_id, update.max_date, update.date, 1, null);
            }
        }

        return true;
    }

    private boolean updatePrintingUsersWithNewMessages(long uid, ArrayList<MessageObject> messages) {
        if (uid > 0) {
            ArrayList<PrintingUser> arr = printingUsers.get(uid);
            if (arr != null) {
                printingUsers.remove(uid);
                return true;
            }
        } else if (uid < 0) {
            ArrayList<Integer> messagesUsers = new ArrayList<Integer>();
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
        MessageObject lastMessage = null;
        TLRPC.TL_dialog dialog = dialogs_dict.get(uid);

        boolean isEncryptedChat = ((int)uid) == 0;

        NotificationCenter.getInstance().postNotificationName(NotificationCenter.didReceivedNewMessages, uid, messages);

        for (MessageObject message : messages) {
            if (lastMessage == null || (!isEncryptedChat && message.messageOwner.id > lastMessage.messageOwner.id || isEncryptedChat && message.messageOwner.id < lastMessage.messageOwner.id) || message.messageOwner.date > lastMessage.messageOwner.date) {
                lastMessage = message;
            }
        }

        boolean changed = false;

        if (dialog == null) {
            if (!isBroadcast) {
                dialog = new TLRPC.TL_dialog();
                dialog.id = uid;
                dialog.unread_count = 0;
                dialog.top_message = lastMessage.messageOwner.id;
                dialog.last_message_date = lastMessage.messageOwner.date;
                dialogs_dict.put(uid, dialog);
                dialogs.add(dialog);
                dialogMessage.put(lastMessage.messageOwner.id, lastMessage);
                changed = true;
            }
        } else {
            boolean change = false;
            if (dialog.top_message > 0 && lastMessage.messageOwner.id > 0 && lastMessage.messageOwner.id > dialog.top_message ||
                    dialog.top_message < 0 && lastMessage.messageOwner.id < 0 && lastMessage.messageOwner.id < dialog.top_message) {
                change = true;
            } else {
                MessageObject currentDialogMessage = dialogMessage.get(dialog.top_message);
                if (currentDialogMessage != null) {
                    if (currentDialogMessage.isSending() && lastMessage.isSending()) {
                        change = true;
                    } else if (dialog.last_message_date < lastMessage.messageOwner.date || dialog.last_message_date == lastMessage.messageOwner.date && lastMessage.isSending()) {
                        change = true;
                    }
                } else {
                    change = true;
                }
            }
            if (change) {
                dialogMessage.remove(dialog.top_message);
                dialog.top_message = lastMessage.messageOwner.id;
                if (!isBroadcast) {
                    dialog.last_message_date = lastMessage.messageOwner.date;
                    changed = true;
                }
                dialogMessage.put(lastMessage.messageOwner.id, lastMessage);
            }
        }

        if (changed) {
            dialogsServerOnly.clear();
            Collections.sort(dialogs, new Comparator<TLRPC.TL_dialog>() {
                @Override
                public int compare(TLRPC.TL_dialog tl_dialog, TLRPC.TL_dialog tl_dialog2) {
                    if (tl_dialog.last_message_date == tl_dialog2.last_message_date) {
                        return 0;
                    } else if (tl_dialog.last_message_date < tl_dialog2.last_message_date) {
                        return 1;
                    } else {
                        return -1;
                    }
                }
            });
            for (TLRPC.TL_dialog d : dialogs) {
                int high_id = (int)(d.id >> 32);
                if ((int)d.id != 0 && high_id != 1) {
                    dialogsServerOnly.add(d);
                }
            }
        }
    }
}
