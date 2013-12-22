/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.messenger;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.app.AlertDialog;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.media.AudioManager;
import android.media.SoundPool;
import android.net.Uri;
import android.os.Build;
import android.os.Vibrator;
import android.provider.BaseColumns;
import android.provider.ContactsContract;
import android.provider.Settings;
import android.support.v4.app.NotificationCompat;
import android.support.v7.app.ActionBarActivity;
import android.text.Html;
import android.util.SparseArray;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.TL.TLClassStore;
import org.telegram.TL.TLObject;
import org.telegram.TL.TLRPC;
import org.telegram.objects.MessageObject;
import org.telegram.ui.ApplicationLoader;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.io.FileInputStream;
import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;

public class MessagesController implements NotificationCenter.NotificationCenterDelegate {
    public ConcurrentHashMap<Integer, TLRPC.Chat> chats = new ConcurrentHashMap<Integer, TLRPC.Chat>(100, 1.0f, 1);
    public ConcurrentHashMap<Integer, TLRPC.EncryptedChat> encryptedChats = new ConcurrentHashMap<Integer, TLRPC.EncryptedChat>(10, 1.0f, 1);
    public ConcurrentHashMap<Integer, TLRPC.User> users = new ConcurrentHashMap<Integer, TLRPC.User>(100, 1.0f, 1);
    public ArrayList<TLRPC.TL_dialog> dialogs = new ArrayList<TLRPC.TL_dialog>();
    public ArrayList<TLRPC.TL_dialog> dialogsServerOnly = new ArrayList<TLRPC.TL_dialog>();
    public ConcurrentHashMap<Long, TLRPC.TL_dialog> dialogs_dict = new ConcurrentHashMap<Long, TLRPC.TL_dialog>(100, 1.0f, 1);
    public SparseArray<MessageObject> dialogMessage = new SparseArray<MessageObject>();
    public ConcurrentHashMap<Long, ArrayList<PrintingUser>> printingUsers = new ConcurrentHashMap<Long, ArrayList<PrintingUser>>(100, 1.0f, 2);
    public HashMap<Long, CharSequence> printingStrings = new HashMap<Long, CharSequence>();

    private HashMap<String, DelayedMessage> delayedMessages = new HashMap<String, DelayedMessage>();
    public SparseArray<MessageObject> sendingMessages = new SparseArray<MessageObject>();
    public SparseArray<TLRPC.User> hidenAddToContacts = new SparseArray<TLRPC.User>();
    private SparseArray<TLRPC.EncryptedChat> acceptingChats = new SparseArray<TLRPC.EncryptedChat>();

    private boolean gettingNewDeleteTask = false;
    private int currentDeletingTaskTime = 0;
    private Long currentDeletingTask = null;
    private ArrayList<Integer> currentDeletingTaskMids = null;

    public int totalDialogsCount = 0;
    public boolean loadingDialogs = false;
    public boolean dialogsEndReached = false;
    public boolean gettingDifference = false;
    public boolean gettingDifferenceAgain = false;
    public boolean updatingState = false;
    public boolean firstGettingTask = false;
    public boolean registeringForPush = false;
    private long lastSoundPlay = 0;
    private long lastStatusUpdateTime = 0;
    public boolean loadingContacts = true;
    private boolean offlineSended = false;
    private String uploadingAvatar = null;
    private SoundPool soundPool;
    private int sound;
    public static SecureRandom random = new SecureRandom();
    private Account currentAccount;

    static {
        try {
            File URANDOM_FILE = new File("/dev/urandom");
            FileInputStream sUrandomIn = new FileInputStream(URANDOM_FILE);
            byte[] buffer = new byte[1024];
            sUrandomIn.read(buffer);
            sUrandomIn.close();
            random.setSeed(buffer);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }


    public static final int MESSAGE_SEND_STATE_SENDING = 1;
    public static final int MESSAGE_SEND_STATE_SENT = 0;
    public static final int MESSAGE_SEND_STATE_SEND_ERROR = 2;

    public static final int UPDATE_MASK_NAME = 1;
    public static final int UPDATE_MASK_AVATAR = 2;
    public static final int UPDATE_MASK_STATUS = 4;
    public static final int UPDATE_MASK_ALL = UPDATE_MASK_AVATAR | UPDATE_MASK_STATUS | UPDATE_MASK_NAME;

    public long openned_dialog_id;

    public static class PrintingUser {
        public long lastTime;
        public int userId;
    }

    private class DelayedMessage {
        public TLRPC.TL_messages_sendMedia sendRequest;
        public TLRPC.TL_decryptedMessage sendEncryptedRequest;
        public int type;
        public TLRPC.FileLocation location;
        public TLRPC.TL_video videoLocation;
        public MessageObject obj;
        public TLRPC.EncryptedChat encryptedChat;
    }

    public static class Contact {
        public int id;
        public ArrayList<String> phones = new ArrayList<String>();
        public ArrayList<String> phoneTypes = new ArrayList<String>();
        public String first_name;
        public String last_name;
    }

    public HashMap<Integer, Contact> contactsBook = new HashMap<Integer, Contact>();
    public HashMap<String, ArrayList<Contact>> contactsSectionsDict = new HashMap<String, ArrayList<Contact>>();
    public ArrayList<String> sortedContactsSectionsArray = new ArrayList<String>();

    public ArrayList<TLRPC.TL_contact> contacts = new ArrayList<TLRPC.TL_contact>();
    public SparseArray<TLRPC.TL_contact> contactsDict = new SparseArray<TLRPC.TL_contact>();
    public HashMap<String, TLRPC.TL_contact> contactsByPhones = new HashMap<String, TLRPC.TL_contact>();
    public HashMap<String, ArrayList<TLRPC.TL_contact>> usersSectionsDict = new HashMap<String, ArrayList<TLRPC.TL_contact>>();
    public ArrayList<String> sortedUsersSectionsArray = new ArrayList<String>();

    public static MessagesController Instance = new MessagesController();

    public static final int didReceivedNewMessages = 1;
    public static final int userPrintUpdate = 2;
    public static final int userPrintUpdateAll = 19;
    public static final int updateInterfaces = 3;
    public static final int dialogsNeedReload = 4;
    public static final int closeChats = 5;
    public static final int messagesDeleted = 6;
    public static final int messagesReaded = 7;
    public static final int messagesDidLoaded = 8;

    public static final int messageReceivedByAck = 9;
    public static final int messageReceivedByServer = 10;
    public static final int messageSendError = 11;

    public static final int reloadSearchResults = 12;

    public static final int contactsDidLoaded = 13;
    public static final int contactsBookDidLoaded = 14;

    public static final int chatDidCreated = 15;
    public static final int chatDidFailCreate = 16;

    public static final int chatInfoDidLoaded = 17;

    public static final int mediaDidLoaded = 18;
    public static final int mediaCountDidLoaded = 20;

    public static final int encryptedChatUpdated = 21;
    public static final int messagesReadedEncrypted = 22;
    public static final int encryptedChatCreated = 23;

    public static final int userPhotosLoaded = 24;

    public MessagesController() {
        MessagesStorage storage = MessagesStorage.Instance;
        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidUpload);
        NotificationCenter.Instance.addObserver(this, FileLoader.FileDidFailUpload);
        NotificationCenter.Instance.addObserver(this, 10);
        addSupportUser();

        try {
            soundPool = new SoundPool(1, AudioManager.STREAM_NOTIFICATION, 0);
            sound = soundPool.load(ApplicationLoader.applicationContext, R.raw.sound_a, 1);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    public void addSupportUser() {
        TLRPC.TL_userForeign user = new TLRPC.TL_userForeign();
        user.phone = "333";
        user.id = 333000;
        user.first_name = "Telegram";
        user.last_name = "";
        user.status = null;
        user.photo = new TLRPC.TL_userProfilePhotoEmpty();
        users.put(user.id, user);
    }

    @Override
    public void didReceivedNotification(int id, Object... args) {
        if (id == FileLoader.FileDidUpload) {
            fileDidUploaded((String)args[0], (TLRPC.InputFile)args[1], (TLRPC.InputEncryptedFile)args[2]);
        } else if (id == FileLoader.FileDidFailUpload) {
            fileDidFailedUpload((String) args[0]);
        } else if (id == messageReceivedByServer) {
            Integer msgId = (Integer)args[0];
            MessageObject obj = dialogMessage.get(msgId);
            if (obj != null) {
                Integer newMsgId = (Integer)args[1];
                dialogMessage.remove(msgId);
                dialogMessage.put(newMsgId, obj);
                obj.messageOwner.id = newMsgId;
                obj.messageOwner.send_state = MessagesController.MESSAGE_SEND_STATE_SENT;

                long uid;
                if (obj.messageOwner.to_id.chat_id != 0) {
                    uid = -obj.messageOwner.to_id.chat_id;
                } else {
                    if (obj.messageOwner.to_id.user_id == UserConfig.clientUserId) {
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
                NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
            }
        }
    }

    @Override
    protected void finalize() throws Throwable {
        super.finalize();
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileDidUpload);
        NotificationCenter.Instance.removeObserver(this, FileLoader.FileDidFailUpload);
        NotificationCenter.Instance.removeObserver(this, messageReceivedByServer);
    }

    public void cleanUp() {
        dialogs_dict.clear();
        dialogs.clear();
        dialogsServerOnly.clear();
        acceptingChats.clear();
        users.clear();
        chats.clear();
        sendingMessages.clear();
        delayedMessages.clear();
        dialogMessage.clear();
        printingUsers.clear();
        printingStrings.clear();
        totalDialogsCount = 0;
        contactsBook.clear();
        contactsSectionsDict.clear();
        sortedContactsSectionsArray.clear();
        contacts.clear();
        contactsDict.clear();
        usersSectionsDict.clear();
        sortedUsersSectionsArray.clear();
        contactsByPhones.clear();
        hidenAddToContacts.clear();

        currentDeletingTaskTime = 0;
        currentDeletingTaskMids = null;
        gettingNewDeleteTask = false;
        currentDeletingTask = null;
        loadingContacts = false;
        loadingDialogs = false;
        dialogsEndReached = false;
        gettingDifference = false;
        gettingDifferenceAgain = false;
        firstGettingTask = false;
        updatingState = false;
        lastStatusUpdateTime = 0;
        offlineSended = false;
        registeringForPush = false;
        uploadingAvatar = null;
        addSupportUser();
    }

    public void didAddedNewTask(final int minDate) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (currentDeletingTask == null && !gettingNewDeleteTask || currentDeletingTaskTime != 0 && minDate < currentDeletingTaskTime) {
                    getNewDeleteTask(null);
                }
            }
        });
    }

    public void getNewDeleteTask(final Long oldTask) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                gettingNewDeleteTask = true;
                MessagesStorage.Instance.getNewTask(oldTask);
            }
        });
    }

    private void checkDeletingTask() {
        int currentServerTime = ConnectionsManager.Instance.getCurrentTime();

        if (currentDeletingTask != null && currentDeletingTaskTime != 0 && currentDeletingTaskTime <= currentServerTime) {
            currentDeletingTaskTime = 0;
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    deleteMessages(currentDeletingTaskMids);

                    Utilities.stageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            getNewDeleteTask(currentDeletingTask);
                            currentDeletingTaskTime = 0;
                            currentDeletingTask = null;
                        }
                    });
                }
            });
        }
    }

    public void processLoadedDeleteTask(final Long taskId, final int taskTime, final ArrayList<Integer> messages) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                gettingNewDeleteTask = false;
                if (taskId != null) {
                    currentDeletingTaskTime = taskTime;
                    currentDeletingTask = taskId;
                    currentDeletingTaskMids = messages;

                    checkDeletingTask();
                } else {
                    currentDeletingTaskTime = 0;
                    currentDeletingTask = null;
                    currentDeletingTaskMids = null;
                }
            }
        });
    }

    public void checkAppAccount() {
        AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
        Account[] accounts = am.getAccountsByType("org.telegram.messenger.account");
        boolean recreateAccount = false;
        if (UserConfig.currentUser != null) {
            if (accounts.length == 1) {
                Account acc = accounts[0];
                if (!acc.name.equals(UserConfig.currentUser.phone)) {
                    recreateAccount = true;
                } else {
                    currentAccount = acc;
                }
            } else {
                recreateAccount = true;
            }
        } else {
            if (accounts.length > 0) {
                recreateAccount = true;
            }
        }
        if (recreateAccount) {
            for (Account c : accounts) {
                am.removeAccount(c, null, null);
            }
            if (UserConfig.currentUser != null) {
                currentAccount = new Account(UserConfig.currentUser.phone, "org.telegram.messenger.account");
                am.addAccountExplicitly(currentAccount, "", null);
            }
        }
    }

    public void deleteAllAppAccounts() {
        try {
            AccountManager am = AccountManager.get(ApplicationLoader.applicationContext);
            Account[] accounts = am.getAccountsByType("org.telegram.messenger.account");
            for (Account c : accounts) {
                am.removeAccount(c, null, null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void loadUserPhotos(final int uid, final int offset, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (fromCache) {
            MessagesStorage.Instance.getUserPhotos(uid, offset, count, max_id, classGuid);
        } else {
            TLRPC.User user = users.get(uid);
            if (user == null) {
                return;
            }
            TLRPC.TL_photos_getUserPhotos req = new TLRPC.TL_photos_getUserPhotos();
            req.limit = count;
            req.offset = offset;
            req.max_id = (int)max_id;
            TLRPC.InputUser inputUser;
            if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                inputUser = new TLRPC.TL_inputUserForeign();
                inputUser.user_id = user.id;
                inputUser.access_hash = user.access_hash;
            } else {
                inputUser = new TLRPC.TL_inputUserContact();
                inputUser.user_id = user.id;
            }
            req.user_id = inputUser;
            long reqId = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.photos_Photos res = (TLRPC.photos_Photos)response;
                        processLoadedUserPhotos(res, uid, offset, count, max_id, fromCache, classGuid);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
            ConnectionsManager.Instance.bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedUserPhotos(final TLRPC.photos_Photos res, final int uid, final int offset, final int count, final long max_id, final boolean fromCache, final int classGuid) {
        if (!fromCache) {
            MessagesStorage.Instance.putUserPhotos(uid, res);
        } else if (res == null || res.photos.isEmpty()) {
            loadUserPhotos(uid, offset, count, max_id, false, classGuid);
            return;
        }
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                NotificationCenter.Instance.postNotificationName(userPhotosLoaded, uid, offset, count, fromCache, classGuid, res.photos);
            }
        });
    }

    public void processLoadedMedia(final TLRPC.messages_Messages res, final long uid, int offset, int count, int max_id, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache && res.messages.isEmpty() && lower_part != 0) {
            loadMedia(uid, offset, count, max_id, false, classGuid);
        } else {
            if (!fromCache) {
                MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, true, true);
                MessagesStorage.Instance.putMedia(uid, res.messages);
            }

            final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
            for (TLRPC.User u : res.users) {
                usersLocal.put(u.id, u);
            }
            final ArrayList<MessageObject> objects = new ArrayList<MessageObject>();
            for (TLRPC.Message message : res.messages) {
                objects.add(new MessageObject(message, usersLocal));
            }

            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    int totalCount;
                    if (res instanceof TLRPC.TL_messages_messagesSlice) {
                        totalCount = res.count;
                    } else {
                        totalCount = res.messages.size();
                    }
                    for (TLRPC.User user : res.users) {
                        if (fromCache) {
                            users.putIfAbsent(user.id, user);
                        } else {
                            users.put(user.id, user);
                            if (user.id == UserConfig.clientUserId) {
                                UserConfig.currentUser = user;
                            }
                        }
                    }
                    for (TLRPC.Chat chat : res.chats) {
                        if (fromCache) {
                            chats.putIfAbsent(chat.id, chat);
                        } else {
                            chats.put(chat.id, chat);
                        }
                    }
                    NotificationCenter.Instance.postNotificationName(mediaDidLoaded, uid, totalCount, objects, fromCache, classGuid);
                }
            });
        }
    }

    public void loadMedia(final long uid, final int offset, final int count, final int max_id, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            MessagesStorage.Instance.loadMedia(uid, offset, count, max_id, classGuid);
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
                TLRPC.User user = users.get(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                }
                req.peer.user_id = lower_part;
            }
            long reqId = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages)response;
                        processLoadedMedia(res, uid, offset, count, max_id, false, classGuid);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
            ConnectionsManager.Instance.bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedMediaCount(final int count, final long uid, final int classGuid, final boolean fromCache) {
        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                int lower_part = (int)uid;
                if (fromCache && count == -1 && lower_part != 0) {
                    getMediaCount(uid, classGuid, false);
                } else {
                    if (!fromCache) {
                        MessagesStorage.Instance.putMediaCount(uid, count);
                    }
                    if (fromCache && count == -1) {
                        NotificationCenter.Instance.postNotificationName(mediaCountDidLoaded, uid, 0, fromCache);
                    } else {
                        NotificationCenter.Instance.postNotificationName(mediaCountDidLoaded, uid, count, fromCache);
                    }
                }
            }
        });
    }

    public void getMediaCount(final long uid, final int classGuid, boolean fromCache) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            MessagesStorage.Instance.getMediaCount(uid, classGuid);
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
                TLRPC.User user = users.get(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                }
                req.peer.user_id = lower_part;
            }
            long reqId = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages)response;
                        if (res instanceof TLRPC.TL_messages_messagesSlice) {
                            processLoadedMediaCount(res.count, uid, classGuid, false);
                        } else {
                            processLoadedMediaCount(res.messages.size(), uid, classGuid, false);
                        }
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
            ConnectionsManager.Instance.bindRequestToGuid(reqId, classGuid);
        }
    }

    public void uploadAndApplyUserAvatar(TLRPC.PhotoSize bigPhoto) {
        if (bigPhoto != null) {
            uploadingAvatar = Utilities.getCacheDir() + "/" + bigPhoto.location.volume_id + "_" + bigPhoto.location.local_id + ".jpg";
            FileLoader.Instance.uploadFile(uploadingAvatar, null, null);
        }
    }

    public void readContacts() {
        if (contactsBook.size() != 0) {
            return;
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileLog.e("tmessages", "start read contacts from phone");
                final HashMap<Integer, Contact> contactsMap = new HashMap<Integer, Contact>();
                final HashMap<String, ArrayList<Contact>> sectionsDict = new HashMap<String, ArrayList<Contact>>();
                final ArrayList<String> sortedSectionsArray = new ArrayList<String>();

                ContentResolver cr = ApplicationLoader.applicationContext.getContentResolver();

                String[] projectioPhones = {
                        ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
                        ContactsContract.CommonDataKinds.Phone.NUMBER,
                        ContactsContract.CommonDataKinds.Phone.TYPE,
                        ContactsContract.CommonDataKinds.Phone.LABEL
                };
                String ids = "";
                Cursor pCur = cr.query(ContactsContract.CommonDataKinds.Phone.CONTENT_URI, projectioPhones, null, null, null);
                if (pCur != null) {
                    if (pCur.getCount() > 0) {
                        while (pCur.moveToNext()) {
                            String number = pCur.getString(1);
                            if (number == null || number.length() == 0) {
                                continue;
                            }
                            Integer id = pCur.getInt(0);
                            if (ids.length() != 0) {
                                ids += ",";
                            }
                            ids += id;

                            int type = pCur.getInt(2);
                            Contact contact = contactsMap.get(id);
                            if (contact == null) {
                                contact = new Contact();
                                contact.first_name = "";
                                contact.last_name = "";
                                contactsMap.put(id, contact);
                                contact.id = id;
                            }

                            contact.phones.add(number);
                            if (type == ContactsContract.CommonDataKinds.Phone.TYPE_CUSTOM) {
                                contact.phoneTypes.add(pCur.getString(3));
                            } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_HOME) {
                                contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneHome));
                            } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) {
                                contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneMobile));
                            } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_WORK) {
                                contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneWork));
                            } else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MAIN) {
                                contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneMain));
                            } else {
                                contact.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneOther));
                            }
                        }
                    }
                    pCur.close();
                }


                String[] projectionNames = {
                        ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID,
                        ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME,
                        ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME,
                        ContactsContract.Data.DISPLAY_NAME,
                        ContactsContract.CommonDataKinds.StructuredName.MIDDLE_NAME
                };
                pCur = cr.query(ContactsContract.Data.CONTENT_URI, projectionNames, ContactsContract.CommonDataKinds.StructuredName.CONTACT_ID + " IN (" + ids + ") AND " + ContactsContract.Data.MIMETYPE + " = '" + ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE + "'", null, null);
                if (pCur != null && pCur.getCount() > 0) {
                    while (pCur.moveToNext()) {
                        int id = pCur.getInt(0);
                        String fname = pCur.getString(1);
                        String sname = pCur.getString(2);
                        String sname2 = pCur.getString(3);
                        String mname = pCur.getString(4);
                        Contact contact = contactsMap.get(id);
                        if (contact != null) {
                            contact.first_name = fname;
                            contact.last_name = sname;
                            if (contact.first_name == null) {
                                contact.first_name = "";
                            }
                            if (mname != null && mname.length() != 0) {
                                if (contact.first_name.length() != 0) {
                                    contact.first_name += " " + mname;
                                } else {
                                    contact.first_name = mname;
                                }
                            }
                            if (contact.last_name == null) {
                                contact.last_name = "";
                            }
                            if (contact.last_name.length() == 0 && contact.first_name.length() == 0 && sname2 != null && sname2.length() != 0) {
                                contact.first_name = sname2;
                            }
                        }
                    }
                    pCur.close();
                }

                ArrayList<TLRPC.TL_inputPhoneContact> toImport = new ArrayList<TLRPC.TL_inputPhoneContact>();

                String contactsImportHash = "";
                MessageDigest mdEnc = null;
                try {
                    mdEnc = MessageDigest.getInstance("MD5");
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                for (HashMap.Entry<Integer, Contact> pair : contactsMap.entrySet()) {
                    Contact value = pair.getValue();
                    int id = pair.getKey();

                    for (int a = 0; a < value.phones.size(); a++) {
                        TLRPC.TL_inputPhoneContact imp = new TLRPC.TL_inputPhoneContact();
                        imp.client_id = id;
                        imp.first_name = value.first_name;
                        imp.last_name = value.last_name;
                        imp.phone = PhoneFormat.stripExceptNumbers(value.phones.get(a));
                        toImport.add(imp);
                        String str = imp.client_id + imp.first_name + imp.last_name + imp.phone;
                        if (mdEnc != null) {
                            mdEnc.update(str.getBytes());
                        }
                    }

                    String key = value.first_name;
                    if (key.length() == 0) {
                        key = value.last_name;
                    }
                    if (key.length() == 0) {
                        key = "#";
                        if (value.phones.size() != 0) {
                            value.first_name = "+" + value.phones.get(0);
                        }
                    } else {
                        key = key.toUpperCase();
                    }
                    if (key.length() > 1) {
                        key = key.substring(0, 1);
                    }
                    ArrayList<Contact> arr = sectionsDict.get(key);
                    if (arr == null) {
                        arr = new ArrayList<Contact>();
                        sectionsDict.put(key, arr);
                        sortedSectionsArray.add(key);
                    }
                    arr.add(value);
                }
                for (HashMap.Entry<String, ArrayList<Contact>> entry : sectionsDict.entrySet()) {
                    Collections.sort(entry.getValue(), new Comparator<Contact>() {
                        @Override
                        public int compare(Contact contact, Contact contact2) {
                            String toComapre1 = contact.first_name;
                            if (toComapre1.length() == 0) {
                                toComapre1 = contact.last_name;
                            }
                            String toComapre2 = contact2.first_name;
                            if (toComapre2.length() == 0) {
                                toComapre2 = contact2.last_name;
                            }
                            return toComapre1.compareTo(toComapre2);
                        }
                    });
                }
                Collections.sort(sortedSectionsArray, new Comparator<String>() {
                    @Override
                    public int compare(String s, String s2) {
                        char cv1 = s.charAt(0);
                        char cv2 = s2.charAt(0);
                        if (cv1 == '#') {
                            return 1;
                        } else if (cv2 == '#') {
                            return -1;
                        }/* else if (cv1 >= 'A' && cv1 <= 'Z' && cv2 >= 'A' && cv2 <= 'Z') {
                            return s.compareTo(s2);
                        } else if (cv1 >= 'A' && cv1 <= 'Z' && !(cv2 >= 'A' && cv2 <= 'Z')) {
                            return 1;
                        } else if (!(cv1 >= 'A' && cv1 <= 'Z') && cv2 >= 'A' && cv2 <= 'Z') {
                            return -1;
                        }*/
                        return s.compareTo(s2);
                    }
                });

                String importHash = String.format(Locale.US, "%32s", new BigInteger(1, mdEnc.digest()).toString(16)).replace(' ', '0');

                if (!toImport.isEmpty() && !UserConfig.importHash.equals(importHash)) {
                    UserConfig.importHash = importHash;
                    UserConfig.saveConfig(false);
                    importContacts(toImport);
                } else {
                    loadContacts(true);
                }

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        contactsBook = contactsMap;
                        contactsSectionsDict = sectionsDict;
                        sortedContactsSectionsArray = sortedSectionsArray;
                        NotificationCenter.Instance.postNotificationName(contactsBookDidLoaded);
                    }
                });
            }
        });

    }

    private void performSyncContacts() {
        Utilities.globalQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    Uri rawContactUri = ContactsContract.RawContacts.CONTENT_URI.buildUpon().appendQueryParameter(ContactsContract.RawContacts.ACCOUNT_NAME, currentAccount.name).appendQueryParameter(
                            ContactsContract.RawContacts.ACCOUNT_TYPE, currentAccount.type).build();
                    Cursor c1 = ApplicationLoader.applicationContext.getContentResolver().query(rawContactUri, new String[]{BaseColumns._ID, ContactsContract.RawContacts.SYNC2}, null, null, null);
                    HashMap<Integer, Long> bookContacts = new HashMap<Integer, Long>();
                    if (c1 != null) {
                        while (c1.moveToNext()) {
                            bookContacts.put(c1.getInt(1), c1.getLong(0));
                        }
                        c1.close();

                        for (TLRPC.TL_contact u : contacts) {
                            if (!bookContacts.containsKey(u.user_id)) {
                                TLRPC.User user = users.get(u.user_id);
                                addContact(currentAccount, user, user.phone);
                            }
                        }
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public static long addContact(Account account, TLRPC.User user, String phone) {
        if (account == null || user == null || phone == null) {
            return -1;
        }
        ArrayList<ContentProviderOperation> query = new ArrayList<ContentProviderOperation>();

        ContentProviderOperation.Builder builder = ContentProviderOperation.newInsert(ContactsContract.RawContacts.CONTENT_URI);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_NAME, account.name);
        builder.withValue(ContactsContract.RawContacts.ACCOUNT_TYPE, account.type);
        builder.withValue(ContactsContract.RawContacts.SYNC1, phone);
        builder.withValue(ContactsContract.RawContacts.SYNC2, user.id);
        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.StructuredName.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.GIVEN_NAME, user.first_name);
        builder.withValue(ContactsContract.CommonDataKinds.StructuredName.FAMILY_NAME, user.last_name);
        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.CommonDataKinds.StructuredName.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE, ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE);
        builder.withValue(ContactsContract.CommonDataKinds.Phone.NUMBER, "+" + phone);
        builder.withValue(ContactsContract.CommonDataKinds.Phone.TYPE, ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE);
        query.add(builder.build());

        builder = ContentProviderOperation.newInsert(ContactsContract.Data.CONTENT_URI);
        builder.withValueBackReference(ContactsContract.Data.RAW_CONTACT_ID, 0);
        builder.withValue(ContactsContract.Data.MIMETYPE, "vnd.android.cursor.item/vnd.org.telegram.messenger.android.profile");
        builder.withValue(ContactsContract.Data.DATA1, "+" + phone);
        builder.withValue(ContactsContract.Data.DATA2, "Telegram Profile");
        builder.withValue(ContactsContract.Data.DATA3, "+" + phone);
        builder.withValue(ContactsContract.Data.DATA4, user.id);
        query.add(builder.build());
        try {
            ContentProviderResult[] result = ApplicationLoader.applicationContext.getContentResolver().applyBatch(ContactsContract.AUTHORITY, query);
            return Long.parseLong(result[0].uri.getLastPathSegment());
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
        return -1;
    }

    public void deleteMessages(ArrayList<Integer> messages) {
        for (Integer id : messages) {
            MessageObject obj = dialogMessage.get(id);
            if (obj != null) {
                obj.deleted = true;
            }
        }
        MessagesStorage.Instance.markMessagesAsDeleted(messages, true);
        MessagesStorage.Instance.updateDialogsWithDeletedMessages(messages, true);
        NotificationCenter.Instance.postNotificationName(messagesDeleted, messages);

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
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void deleteDialog(final long did, int offset, final boolean onlyHistory) {
        TLRPC.TL_dialog dialog = dialogs_dict.get(did);
        if (dialog != null) {
            int lower_part = (int)did;

            if (offset == 0) {
                if (!onlyHistory) {
                    dialogs.remove(dialog);
                    dialogsServerOnly.remove(dialog);
                    dialogs_dict.remove(did);
                    totalDialogsCount--;
                }
                dialogMessage.remove(dialog.top_message);
                MessagesStorage.Instance.deleteDialog(did, onlyHistory);
                NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
            }

            if (lower_part != 0) {
                TLRPC.TL_messages_deleteHistory req = new TLRPC.TL_messages_deleteHistory();
                req.offset = offset;
                if (did < 0) {
                    req.peer = new TLRPC.TL_inputPeerChat();
                    req.peer.chat_id = -lower_part;
                } else {
                    TLRPC.User user = users.get(lower_part);
                    if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                        req.peer = new TLRPC.TL_inputPeerForeign();
                        req.peer.access_hash = user.access_hash;
                    } else {
                        req.peer = new TLRPC.TL_inputPeerContact();
                    }
                    req.peer.user_id = lower_part;
                }
                ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory)response;
                            if (res.offset > 0) {
                                deleteDialog(did, res.offset, onlyHistory);
                            }
                            if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                                MessagesStorage.lastSeqValue = res.seq;
                                MessagesStorage.lastPtsValue = res.pts;
                                MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                            } else if (MessagesStorage.lastSeqValue != res.seq) {
                                getDifference();
                            }
                        }
                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric);
            } else {
                int encId = (int)(did >> 32);
                declineSecretChat(encId);
            }
        }
    }

    public void loadChatInfo(final int chat_id) {
        MessagesStorage.Instance.loadChatInfo(chat_id);
    }

    public void processChatInfo(final int chat_id, final TLRPC.ChatParticipants info, final ArrayList<TLRPC.User> usersArr, final boolean fromCache) {
        if (info == null && fromCache) {
            TLRPC.TL_messages_getFullChat req = new TLRPC.TL_messages_getFullChat();
            req.chat_id = chat_id;
            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error != null) {
                        return;
                    }
                    final TLRPC.TL_messages_chatFull res = (TLRPC.TL_messages_chatFull)response;
                    MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, true, true);
                    MessagesStorage.Instance.updateChatInfo(chat_id, res.full_chat.participants, false);
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            for (TLRPC.User user : res.users) {
                                users.put(user.id, user);
                                if (user.id == UserConfig.clientUserId) {
                                    UserConfig.currentUser = user;
                                }
                            }
                            for (TLRPC.Chat chat : res.chats) {
                                chats.put(chat.id, chat);
                            }
                            NotificationCenter.Instance.postNotificationName(chatInfoDidLoaded, chat_id, res.full_chat.participants);
                        }
                    });
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
        } else {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    for (TLRPC.User user : usersArr) {
                        if (fromCache) {
                            users.putIfAbsent(user.id, user);
                        } else {
                            users.put(user.id, user);
                            if (user.id == UserConfig.clientUserId) {
                                UserConfig.currentUser = user;
                            }
                        }
                    }
                    NotificationCenter.Instance.postNotificationName(chatInfoDidLoaded, chat_id, info);
                }
            });
        }
    }

    public void updateTimerProc() {
        long currentTime = System.currentTimeMillis();

        checkDeletingTask();

        if (UserConfig.clientUserId != 0) {
            if (ApplicationLoader.lastPauseTime == 0) {
                if (lastStatusUpdateTime != -1 && (lastStatusUpdateTime == 0 || lastStatusUpdateTime <= System.currentTimeMillis() - 55000 || offlineSended)) {
                    lastStatusUpdateTime = -1;
                    TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                    req.offline = false;
                    ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            lastStatusUpdateTime = System.currentTimeMillis();
                        }
                    }, null, true, RPCRequest.RPCRequestClassGeneric);
                    offlineSended = false;
                }
            } else if (!offlineSended && ApplicationLoader.lastPauseTime <= System.currentTimeMillis() - 2000) {
                TLRPC.TL_account_updateStatus req = new TLRPC.TL_account_updateStatus();
                req.offline = true;
                ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric);
                offlineSended = true;
            }
        }
        final ArrayList<Long> uidsToSend = new ArrayList<Long>();
        if (!printingUsers.isEmpty()) {
            ArrayList<Long> keys = new ArrayList<Long>(printingUsers.keySet());
            for (int b = 0; b < keys.size(); b++) {
                Long key = keys.get(b);
                ArrayList<PrintingUser> arr = printingUsers.get(key);
                for (int a = 0; a < arr.size(); a++) {
                    PrintingUser user = arr.get(a);
                    if (user.lastTime + 5900 < currentTime) {
                        if (!uidsToSend.contains(key)) {
                            uidsToSend.add(key);
                        }
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

            if (!uidsToSend.isEmpty()) {
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (Long uid : uidsToSend) {
                            NotificationCenter.Instance.postNotificationName(userPrintUpdate, uid);
                        }
                        NotificationCenter.Instance.postNotificationName(userPrintUpdateAll);
                    }
                });
            }
        }
    }

    public void updatePrintingStrings() {
        final HashMap<Long, CharSequence> newPrintingStrings = new HashMap<Long, CharSequence>();

        ArrayList<Long> keys = new ArrayList<Long>(printingUsers.keySet());
        for (Long key : keys) {
            if (key > 0) {
                newPrintingStrings.put(key, ApplicationLoader.applicationContext.getString(R.string.Typing));
            } else {
                ArrayList<PrintingUser> arr = printingUsers.get(key);
                int count = 0;
                String label = "";
                for (PrintingUser pu : arr) {
                    TLRPC.User user = users.get(pu.userId);
                    if (user != null) {
                        if (label.length() != 0) {
                            label += ", ";
                        }
                        label += Utilities.formatName(user.first_name, user.last_name);
                        count++;
                    }
                    if (count == 2) {
                        break;
                    }
                }
                if (label.length() != 0) {
                    if (count > 1) {
                        if (arr.size() > 2) {
                            newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s %s", label, String.format(ApplicationLoader.applicationContext.getString(R.string.AndMoreTyping), arr.size() - 2), ApplicationLoader.applicationContext.getString(R.string.AreTyping))));
                        } else {
                            newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, ApplicationLoader.applicationContext.getString(R.string.AreTyping))));
                        }
                    } else {
                        newPrintingStrings.put(key, Html.fromHtml(String.format("%s %s", label, ApplicationLoader.applicationContext.getString(R.string.IsTyping))));
                    }
                }
            }
        }

        Utilities.RunOnUIThread(new Runnable() {
            @Override
            public void run() {
                printingStrings = newPrintingStrings;
            }
        });
    }

    public void processLoadedContacts(final ArrayList<TLRPC.TL_contact> contactsArr, final ArrayList<TLRPC.User> usersArr, final int from) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                FileLog.e("tmessages", "done loading contacts");
                if (from == 1 && contactsArr.isEmpty()) {
                    loadContacts(false);
                    return;
                }
                if (from == 0 || from == 2 || from == 3) {
                    MessagesStorage.Instance.putUsersAndChats(usersArr, null, true, true);
                    MessagesStorage.Instance.putContacts(contactsArr, true);
                    Collections.sort(contacts, new Comparator<TLRPC.TL_contact>() {
                        @Override
                        public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                            if (tl_contact.user_id > tl_contact2.user_id) {
                                return 1;
                            } else if (tl_contact.user_id < tl_contact2.user_id) {
                                return -1;
                            }
                            return 0;
                        }
                    });
                    String ids = "";
                    for (TLRPC.TL_contact aContactsArr : contactsArr) {
                        if (ids.length() != 0) {
                            ids += ",";
                        }
                        ids += aContactsArr.user_id;
                    }
                    UserConfig.contactsHash = Utilities.MD5(ids);
                    UserConfig.saveConfig(false);
                    if (from == 2) {
                        loadContacts(false);
                    }
                }
                final HashMap<Integer, TLRPC.User> usersDict = new HashMap<Integer, TLRPC.User>();
                for (TLRPC.User user : usersArr) {
                    usersDict.put(user.id, user);
                }
                Collections.sort(contactsArr, new Comparator<TLRPC.TL_contact>() {
                    @Override
                    public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                        TLRPC.User user1 = usersDict.get(tl_contact.user_id);
                        TLRPC.User user2 = usersDict.get(tl_contact2.user_id);
                        String name1 = user1.first_name;
                        if (name1 == null || name1.length() == 0) {
                            name1 = user1.last_name;
                        }
                        String name2 = user2.first_name;
                        if (name2 == null || name2.length() == 0) {
                            name2 = user2.last_name;
                        }
                        return name1.compareTo(name2);
                    }
                });

                final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<String, ArrayList<TLRPC.TL_contact>>();
                final SparseArray<TLRPC.TL_contact> contactsDictionery = new SparseArray<TLRPC.TL_contact>();
                final ArrayList<String> sortedSectionsArray = new ArrayList<String>();
                final HashMap<String, TLRPC.TL_contact> contactsPhones = new HashMap<String, TLRPC.TL_contact>();

                for (TLRPC.TL_contact value : contactsArr) {
                    TLRPC.User user = usersDict.get(value.user_id);
                    if (user == null) {
                        continue;
                    }
                    contactsDictionery.put(value.user_id, value);
                    contactsPhones.put(user.phone, value);

                    String key = user.first_name;
                    if (key == null || key.length() == 0) {
                        key = user.last_name;
                    }
                    if (key.length() == 0) {
                        key = "#";
                    } else {
                        key = key.toUpperCase();
                    }
                    if (key.length() > 1) {
                        key = key.substring(0, 1);
                    }
                    ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
                    if (arr == null) {
                        arr = new ArrayList<TLRPC.TL_contact>();
                        sectionsDict.put(key, arr);
                        sortedSectionsArray.add(key);
                    }
                    arr.add(value);
                }
                for (HashMap.Entry<String, ArrayList<TLRPC.TL_contact>> entry : sectionsDict.entrySet()) {
                    Collections.sort(entry.getValue(), new Comparator<TLRPC.TL_contact>() {
                        @Override
                        public int compare(TLRPC.TL_contact contact, TLRPC.TL_contact contact2) {
                            TLRPC.User user1 = usersDict.get(contact.user_id);
                            TLRPC.User user2 = usersDict.get(contact2.user_id);
                            String toComapre1 = user1.first_name;
                            if (toComapre1 == null || toComapre1.length() == 0) {
                                toComapre1 = user1.last_name;
                            }
                            String toComapre2 = user2.first_name;
                            if (toComapre2 == null || toComapre2.length() == 0) {
                                toComapre2 = user2.last_name;
                            }
                            return toComapre1.compareTo(toComapre2);
                        }
                    });
                }
                Collections.sort(sortedSectionsArray, new Comparator<String>() {
                    @Override
                    public int compare(String s, String s2) {
                        char cv1 = s.charAt(0);
                        char cv2 = s2.charAt(0);
                        if (cv1 == '#') {
                            return 1;
                        } else if (cv2 == '#') {
                            return -1;
                        }/* else if (cv1 >= 'A' && cv1 <= 'Z' && cv2 >= 'A' && cv2 <= 'Z') {
                            return s.compareTo(s2);
                        } else if (cv1 >= 'A' && cv1 <= 'Z' && !(cv2 >= 'A' && cv2 <= 'Z')) {
                            return 1;
                        } else if (!(cv1 >= 'A' && cv1 <= 'Z') && cv2 >= 'A' && cv2 <= 'Z') {
                            return -1;
                        }*/
                        return s.compareTo(s2);
                    }
                });

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : usersArr) {
                            if (from == 1) {
                                users.putIfAbsent(user.id, user);
                            } else {
                                users.put(user.id, user);
                                if (user.id == UserConfig.clientUserId) {
                                    UserConfig.currentUser = user;
                                }
                            }
                        }
                        contacts = contactsArr;
                        contactsByPhones = contactsPhones;
                        contactsDict = contactsDictionery;
                        usersSectionsDict = sectionsDict;
                        sortedUsersSectionsArray = sortedSectionsArray;
                        if (from == 0) {
                            loadingContacts = false;
                        }
                        performSyncContacts();
                        NotificationCenter.Instance.postNotificationName(contactsDidLoaded);
                    }
                });
            }
        });

    }

    public void sendTyping(long dialog_id, int classGuid) {
        if (dialog_id == 0) {
            return;
        }
        int lower_part = (int)dialog_id;
        if (lower_part != 0) {
            TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
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
            req.typing = true;
            long reqId = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
            ConnectionsManager.Instance.bindRequestToGuid(reqId, classGuid);
        } else {
            int encId = (int)(dialog_id >> 32);
            TLRPC.EncryptedChat chat = encryptedChats.get(encId);
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_setEncryptedTyping req = new TLRPC.TL_messages_setEncryptedTyping();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.typing = true;
                long reqId = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric);
                ConnectionsManager.Instance.bindRequestToGuid(reqId, classGuid);
            }
        }
    }

    public void loadContacts(boolean fromCache) {
        loadingContacts = true;
        if (fromCache) {
            FileLog.e("tmessages", "load contacts from cache");
            MessagesStorage.Instance.getContacts();
        } else {
            FileLog.e("tmessages", "load contacts from server");
            TLRPC.TL_contacts_getContacts req = new TLRPC.TL_contacts_getContacts();
            req.hash = UserConfig.contactsHash;
            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        TLRPC.contacts_Contacts res = (TLRPC.contacts_Contacts)response;
                        if (res instanceof TLRPC.TL_contacts_contactsNotModified) {
                            return;
                        }
                        processLoadedContacts(res.contacts, res.users, 0);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
        }
    }

    public void importContacts(ArrayList<TLRPC.TL_inputPhoneContact> contactsArr) {
        TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
        req.contacts = contactsArr;
        req.replace = false;
        FileLog.e("tmessages", "start import contacts");
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    FileLog.e("tmessages", "contacts imported");
                    TLRPC.TL_contacts_importedContacts res = (TLRPC.TL_contacts_importedContacts)response;
                    MessagesStorage.Instance.putUsersAndChats(res.users, null, true, true);
                    ArrayList<TLRPC.TL_contact> cArr = new ArrayList<TLRPC.TL_contact>();
                    for (TLRPC.TL_importedContact c : res.imported) {
                        TLRPC.TL_contact contact = new TLRPC.TL_contact();
                        contact.user_id = c.user_id;
                        cArr.add(contact);
                    }
                    processLoadedContacts(cArr, res.users, 2);
                } else {
                    FileLog.e("tmessages", "import contacts error " + error.text);
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    public void loadMessages(final long dialog_id, final int offset, final int count, final int max_id, boolean fromCache, int midDate, final int classGuid, boolean from_unread, boolean forward) {
        int lower_part = (int)dialog_id;
        if (fromCache || lower_part == 0) {
            MessagesStorage.Instance.getMessages(dialog_id, offset, count, max_id, midDate, classGuid, from_unread, forward);
        } else {
            TLRPC.TL_messages_getHistory req = new TLRPC.TL_messages_getHistory();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
                if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                    req.peer = new TLRPC.TL_inputPeerForeign();
                    req.peer.user_id = user.id;
                    req.peer.access_hash = user.access_hash;
                } else {
                    req.peer = new TLRPC.TL_inputPeerContact();
                    req.peer.user_id = user.id;
                }
            }
            req.offset = offset;
            req.limit = count;
            req.max_id = max_id;
            long reqId = ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages)response;
                        processLoadedMessages(res, dialog_id, offset, count, max_id, false, classGuid, 0, 0, 0, 0, false);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
            ConnectionsManager.Instance.bindRequestToGuid(reqId, classGuid);
        }
    }

    public void processLoadedMessages(final TLRPC.messages_Messages messagesRes, final long dialog_id, final int offset, final int count, final int max_id, final boolean isCache, final int classGuid, final int first_unread, final int last_unread, final int unread_count, final int last_date, final boolean isForward) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                int lower_id = (int)dialog_id;
                if (!isCache) {
                    MessagesStorage.Instance.putMessages(messagesRes, dialog_id);
                }
                if (lower_id != 0 && isCache && messagesRes.messages.size() == 0 && !isForward) {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadMessages(dialog_id, offset, count, max_id, false, 0, classGuid, false, false);
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
                    objects.add(new MessageObject(message, usersLocal));
                }
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User u : messagesRes.users) {
                            if (isCache) {
                                if (u.id == UserConfig.clientUserId || u.id == 333000) {
                                    users.put(u.id, u);
                                } else {
                                    users.putIfAbsent(u.id, u);
                                }
                            } else {
                                users.put(u.id, u);
                                if (u.id == UserConfig.clientUserId) {
                                    UserConfig.currentUser = u;
                                }
                            }
                        }
                        for (TLRPC.Chat c : messagesRes.chats) {
                            if (isCache) {
                                chats.putIfAbsent(c.id, c);
                            } else {
                                chats.put(c.id, c);
                            }
                        }
                        NotificationCenter.Instance.postNotificationName(messagesDidLoaded, dialog_id, offset, count, objects, isCache, first_unread, last_unread, unread_count, last_date, isForward);
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

        if (fromCache) {
            MessagesStorage.Instance.getDialogs(offset, serverOffset, count);
        } else {
            TLRPC.TL_messages_getDialogs req = new TLRPC.TL_messages_getDialogs();
            req.offset = serverOffset;
            req.limit = count;
            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Dialogs dialogsRes = (TLRPC.messages_Dialogs)response;
                        processLoadedDialogs(dialogsRes, null, offset, serverOffset, count, false, false);
                    }
                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
        }
    }

    public void processDialogsUpdate(final TLRPC.messages_Dialogs dialogsRes, ArrayList<TLRPC.EncryptedChat> encChats) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final HashMap<Long, TLRPC.TL_dialog> new_dialogs_dict = new HashMap<Long, TLRPC.TL_dialog>();
                final HashMap<Integer, MessageObject> new_dialogMessage = new HashMap<Integer, MessageObject>();
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();

                for (TLRPC.User u : dialogsRes.users) {
                    usersLocal.put(u.id, u);
                }

                for (TLRPC.Message m : dialogsRes.messages) {
                    new_dialogMessage.put(m.id, new MessageObject(m, usersLocal));
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

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User u : dialogsRes.users) {
                            users.putIfAbsent(u.id, u);
                        }
                        for (TLRPC.Chat c : dialogsRes.chats) {
                            chats.putIfAbsent(c.id, c);
                        }

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
                            if ((int)d.id != 0) {
                                dialogsServerOnly.add(d);
                            }
                        }
                        NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
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
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            for (TLRPC.User u : dialogsRes.users) {
                                if (isCache) {
                                    if (u.id == UserConfig.clientUserId || u.id == 333000) {
                                        users.put(u.id, u);
                                    } else {
                                        users.putIfAbsent(u.id, u);
                                    }
                                } else {
                                    users.put(u.id, u);
                                    if (u.id == UserConfig.clientUserId) {
                                        UserConfig.currentUser = u;
                                    }
                                }
                            }
                            loadingDialogs = false;
                            if (resetEnd) {
                                dialogsEndReached = false;
                                NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                            }
                            loadDialogs(offset, serverOffset, count, false);
                        }
                    });
                    return;
                }
                final HashMap<Long, TLRPC.TL_dialog> new_dialogs_dict = new HashMap<Long, TLRPC.TL_dialog>();
                final HashMap<Integer, MessageObject> new_dialogMessage = new HashMap<Integer, MessageObject>();
                final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<Integer, TLRPC.User>();
                int new_totalDialogsCount;

                if (!isCache) {
                    MessagesStorage.Instance.putDialogs(dialogsRes);
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
                    new_dialogMessage.put(m.id, new MessageObject(m, usersLocal));
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
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User u : dialogsRes.users) {
                            if (isCache) {
                                if (u.id == UserConfig.clientUserId || u.id == 333000) {
                                    users.put(u.id, u);
                                } else {
                                    users.putIfAbsent(u.id, u);
                                }
                            } else {
                                users.put(u.id, u);
                                if (u.id == UserConfig.clientUserId) {
                                    UserConfig.currentUser = u;
                                }
                            }
                        }
                        for (TLRPC.Chat c : dialogsRes.chats) {
                            if (isCache) {
                                chats.putIfAbsent(c.id, c);
                            } else {
                                chats.put(c.id, c);
                            }
                        }
                        if (encChats != null) {
                            for (TLRPC.EncryptedChat encryptedChat : encChats) {
                                encryptedChats.put(encryptedChat.id, encryptedChat);
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
                            if ((int)d.id != 0) {
                                dialogsServerOnly.add(d);
                            }
                        }

                        dialogsEndReached = (dialogsRes.dialogs.size() == 0 || dialogsRes.dialogs.size() != count) && !isCache;
                        NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                    }
                });
            }
        });
    }

    public TLRPC.TL_photo generatePhotoSizes(String path) {
        long time = System.currentTimeMillis();
        Bitmap bitmap = FileLoader.loadBitmap(path, 800, 800);
        ArrayList<TLRPC.PhotoSize> sizes = new ArrayList<TLRPC.PhotoSize>();
        TLRPC.PhotoSize size = FileLoader.scaleAndSaveImage(bitmap, 90, 90, 55, true);
        if (size != null) {
            size.type = "s";
            sizes.add(size);
        }
        size = FileLoader.scaleAndSaveImage(bitmap, 320, 320, 87, false);
        if (size != null) {
            size.type = "m";
            sizes.add(size);
        }
        size = FileLoader.scaleAndSaveImage(bitmap, 800, 800, 87, false);
        if (size != null) {
            size.type = "x";
            sizes.add(size);
        }
        if (Build.VERSION.SDK_INT < 11) {
            if (bitmap != null) {
                bitmap.recycle();
            }
        }
        if (sizes.isEmpty()) {
            return null;
        } else {
            UserConfig.saveConfig(false);
            TLRPC.TL_photo photo = new TLRPC.TL_photo();
            photo.user_id = UserConfig.clientUserId;
            photo.date = ConnectionsManager.Instance.getCurrentTime();
            photo.sizes = sizes;
            photo.caption = "";
            photo.geo = new TLRPC.TL_geoPointEmpty();
            return photo;
        }
    }

    public void markDialogAsRead(final long dialog_id, final int max_id, final int max_positive_id, final int offset, final int max_date, final boolean was) {
        int lower_part = (int)dialog_id;
        if (lower_part != 0) {
            if (max_id == 0 && offset == 0) {
                return;
            }
            TLRPC.TL_messages_readHistory req = new TLRPC.TL_messages_readHistory();
            if (lower_part < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = users.get(lower_part);
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
            if (offset == 0) {
                MessagesStorage.Instance.processPendingRead(dialog_id, max_positive_id, max_date, false);
            }
            if (req.max_id != Integer.MAX_VALUE) {
                ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        if (error == null) {
                            MessagesStorage.Instance.processPendingRead(dialog_id, max_positive_id, max_date, true);
                            TLRPC.TL_messages_affectedHistory res = (TLRPC.TL_messages_affectedHistory)response;
                            if (res.offset > 0) {
                                markDialogAsRead(dialog_id, 0, max_positive_id, res.offset, max_date, was);
                            }

                            if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                                MessagesStorage.lastSeqValue = res.seq;
                                MessagesStorage.lastPtsValue = res.pts;
                                MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                            } else if (MessagesStorage.lastSeqValue != res.seq) {
                                getDifference();
                            }
                        }
                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric);
            }

            if (offset == 0) {
                TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                if (dialog != null) {
                    dialog.unread_count = 0;
                    NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                }

                TLRPC.TL_messages_receivedMessages req2 = new TLRPC.TL_messages_receivedMessages();
                req2.max_id = max_positive_id;
                ConnectionsManager.Instance.performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
            }
        } else {
            if (max_date == 0) {
                return;
            }
            int encId = (int)(dialog_id >> 32);
            TLRPC.EncryptedChat chat = encryptedChats.get(encId);
            if (chat.auth_key != null && chat.auth_key.length > 1 && chat instanceof TLRPC.TL_encryptedChat) {
                TLRPC.TL_messages_readEncryptedHistory req = new TLRPC.TL_messages_readEncryptedHistory();
                req.peer = new TLRPC.TL_inputEncryptedChat();
                req.peer.chat_id = chat.id;
                req.peer.access_hash = chat.access_hash;
                req.max_date = max_date;

                ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        //MessagesStorage.Instance.processPendingRead(dialog_id, max_id, max_date, true);
                    }
                }, null, true, RPCRequest.RPCRequestClassGeneric);
            }
            MessagesStorage.Instance.processPendingRead(dialog_id, max_id, max_date, false);
            TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
            if (dialog != null) {
                dialog.unread_count = 0;
                NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
            }

            if (chat.ttl > 0 && was) {
                int serverTime = Math.max(ConnectionsManager.Instance.getCurrentTime(), max_date);
                MessagesStorage.Instance.createTaskForDate(chat.id, serverTime, serverTime, 0);
            }
        }
    }

    public void cancelSendingMessage(MessageObject object) {
        String keyToRemvoe = null;
        for (HashMap.Entry<String, DelayedMessage> entry : delayedMessages.entrySet()) {
            if (entry.getValue().obj.messageOwner.id == object.messageOwner.id) {
                keyToRemvoe = entry.getKey();
                break;
            }
        }
        if (keyToRemvoe != null) {
            ArrayList<Integer> messages = new ArrayList<Integer>();
            messages.add(object.messageOwner.id);
            FileLoader.Instance.cancelUploadFile(keyToRemvoe);
            deleteMessages(messages);
        }
    }

    private long getNextRandomId() {
        long val = 0;
        while (val == 0) {
            val = random.nextLong();
        }
        return val;
    }

    public void sendMessage(TLRPC.User user, long peer) {
        sendMessage(null, 0, 0, null, null, null, null, user, peer);
    }

    public void sendMessage(MessageObject message, long peer) {
        sendMessage(null, 0, 0, null, null, message, null, null, peer);
    }

    public void sendMessage(String message, long peer) {
        sendMessage(message, 0, 0, null, null, null, null, null, peer);
    }

    public void sendMessage(TLRPC.FileLocation location, long peer) {
        sendMessage(null, 0, 0, null, null, null, location, null, peer);
    }

    public void sendMessage(double lat, double lon, long peer) {
        sendMessage(null, lat, lon, null, null, null, null, null, peer);
    }

    public void sendMessage(TLRPC.TL_photo photo, long peer) {
        sendMessage(null, 0, 0, photo, null, null, null, null, peer);
    }

    public void sendMessage(TLRPC.TL_video video, long peer) {
        sendMessage(null, 0, 0, null, video, null, null, null, peer);
    }

    public void sendTTLMessage(TLRPC.EncryptedChat encryptedChat) {
        TLRPC.TL_messageService newMsg = new TLRPC.TL_messageService();

        newMsg.action = new TLRPC.TL_messageActionTTLChange();
        newMsg.action.ttl = encryptedChat.ttl;
        newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
        newMsg.from_id = UserConfig.clientUserId;
        newMsg.unread = true;
        newMsg.dialog_id = ((long)encryptedChat.id) << 32;
        newMsg.to_id = new TLRPC.TL_peerUser();
        if (encryptedChat.participant_id == UserConfig.clientUserId) {
            newMsg.to_id.user_id = encryptedChat.admin_id;
        } else {
            newMsg.to_id.user_id = encryptedChat.participant_id;
        }
        newMsg.out = true;
        newMsg.date = ConnectionsManager.Instance.getCurrentTime();
        newMsg.random_id = getNextRandomId();
        UserConfig.saveConfig(false);
        final MessageObject newMsgObj = new MessageObject(newMsg, users);
        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENDING;

        final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
        objArr.add(newMsgObj);
        ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
        arr.add(newMsg);
        MessagesStorage.Instance.putMessages(arr, false, true);
        updateInterfaceWithMessages(newMsg.dialog_id, objArr);
        NotificationCenter.Instance.postNotificationName(dialogsNeedReload);

        sendingMessages.put(newMsg.id, newMsgObj);

        TLRPC.TL_decryptedMessageService reqSend = new TLRPC.TL_decryptedMessageService();
        reqSend.random_id = newMsg.random_id;
        reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(random.nextDouble() * 16))];
        random.nextBytes(reqSend.random_bytes);
        reqSend.action = new TLRPC.TL_decryptedMessageActionSetMessageTTL();
        reqSend.action.ttl_seconds = encryptedChat.ttl;
        performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null);
    }

    private void sendMessage(String message, double lat, double lon, TLRPC.TL_photo photo, TLRPC.TL_video video, MessageObject msgObj, TLRPC.FileLocation location, TLRPC.User user, long peer) {
        TLRPC.Message newMsg = null;
        int type = -1;
        if (message != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaEmpty();
            type = 0;
            newMsg.message = message;
        } else if (lat != 0 && lon != 0) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaGeo();
            newMsg.media.geo = new TLRPC.TL_geoPoint();
            newMsg.media.geo.lat = lat;
            newMsg.media.geo._long = lon;
            newMsg.message = "";
            type = 1;
        } else if (photo != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaPhoto();
            newMsg.media.photo = photo;
            type = 2;
            newMsg.message = "-1";
            TLRPC.FileLocation location1 = photo.sizes.get(photo.sizes.size() - 1).location;
            newMsg.attachPath = Utilities.getCacheDir() + "/" + location1.volume_id + "_" + location1.local_id + ".jpg";
        } else if (video != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaVideo();
            newMsg.media.video = video;
            type = 3;
            newMsg.message = "-1";
            newMsg.attachPath = video.path;
        } else if (msgObj != null) {
            newMsg = new TLRPC.TL_messageForwarded();
            if (msgObj.messageOwner instanceof TLRPC.TL_messageForwarded) {
                newMsg.fwd_from_id = msgObj.messageOwner.fwd_from_id;
                newMsg.fwd_date = msgObj.messageOwner.fwd_date;
                newMsg.media = msgObj.messageOwner.media;
                newMsg.message = msgObj.messageOwner.message;
                newMsg.fwd_msg_id = msgObj.messageOwner.id;
                type = 4;
            } else if (msgObj.type == 11) {
                newMsg.fwd_from_id = msgObj.messageOwner.from_id;
                newMsg.fwd_date = msgObj.messageOwner.date;
                newMsg.media = new TLRPC.TL_messageMediaPhoto();
                newMsg.media.photo = msgObj.messageOwner.action.photo;
                newMsg.message = "";
                newMsg.fwd_msg_id = msgObj.messageOwner.id;
                type = 5;
            } else {
                newMsg.fwd_from_id = msgObj.messageOwner.from_id;
                newMsg.fwd_date = msgObj.messageOwner.date;
                newMsg.media = msgObj.messageOwner.media;
                newMsg.message = msgObj.messageOwner.message;
                newMsg.fwd_msg_id = msgObj.messageOwner.id;
                type = 4;
            }
        } else if (location != null) {

        } else if (user != null) {
            newMsg = new TLRPC.TL_message();
            newMsg.media = new TLRPC.TL_messageMediaContact();
            newMsg.media.phone_number = user.phone;
            newMsg.media.first_name = user.first_name;
            newMsg.media.last_name = user.last_name;
            newMsg.media.user_id = user.id;
            newMsg.message = "";
            type = 6;
        }
        if (newMsg == null) {
            return;
        }
        newMsg.local_id = newMsg.id = UserConfig.getNewMessageId();
        newMsg.from_id = UserConfig.clientUserId;
        newMsg.unread = true;
        newMsg.dialog_id = peer;
        int lower_id = (int)peer;
        TLRPC.EncryptedChat encryptedChat = null;
        TLRPC.InputPeer sendToPeer = null;
        if (lower_id != 0) {
            if (lower_id < 0) {
                newMsg.to_id = new TLRPC.TL_peerChat();
                newMsg.to_id.chat_id = -lower_id;
                sendToPeer = new TLRPC.TL_inputPeerChat();
                sendToPeer.chat_id = -lower_id;
            } else {
                newMsg.to_id = new TLRPC.TL_peerUser();
                newMsg.to_id.user_id = lower_id;

                TLRPC.User sendToUser = users.get(lower_id);
                if (sendToUser == null) {
                    return;
                }
                if (sendToUser instanceof TLRPC.TL_userForeign || sendToUser instanceof TLRPC.TL_userRequest) {
                    sendToPeer = new TLRPC.TL_inputPeerForeign();
                    sendToPeer.user_id = sendToUser.id;
                    sendToPeer.access_hash = sendToUser.access_hash;
                } else {
                    sendToPeer = new TLRPC.TL_inputPeerContact();
                    sendToPeer.user_id = sendToUser.id;
                }
            }
        } else {
            encryptedChat = encryptedChats.get((int)(peer >> 32));
            newMsg.to_id = new TLRPC.TL_peerUser();
            if (encryptedChat.participant_id == UserConfig.clientUserId) {
                newMsg.to_id.user_id = encryptedChat.admin_id;
            } else {
                newMsg.to_id.user_id = encryptedChat.participant_id;
            }
            newMsg.ttl = encryptedChat.ttl;
        }
        newMsg.out = true;
        newMsg.date = ConnectionsManager.Instance.getCurrentTime();
        newMsg.random_id = getNextRandomId();
        UserConfig.saveConfig(false);
        final MessageObject newMsgObj = new MessageObject(newMsg, null);
        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENDING;

        final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
        objArr.add(newMsgObj);
        ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
        arr.add(newMsg);
        MessagesStorage.Instance.putMessages(arr, false, true);
        updateInterfaceWithMessages(peer, objArr);
        NotificationCenter.Instance.postNotificationName(dialogsNeedReload);

        sendingMessages.put(newMsg.id, newMsgObj);

        if (type == 0) {
            if (encryptedChat == null) {
                TLRPC.TL_messages_sendMessage reqSend = new TLRPC.TL_messages_sendMessage();
                reqSend.message = message;
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                performSendMessageRequest(reqSend, newMsgObj);
            } else {
                TLRPC.TL_decryptedMessage reqSend = new TLRPC.TL_decryptedMessage();
                reqSend.random_id = newMsg.random_id;
                reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(random.nextDouble() * 16))];
                random.nextBytes(reqSend.random_bytes);
                reqSend.message = message;
                reqSend.media = new TLRPC.TL_decryptedMessageMediaEmpty();
                performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null);
            }
        } else if (type == 1 || type == 2 || type == 3 || type == 5 || type == 6) {
            if (encryptedChat == null) {
                TLRPC.TL_messages_sendMedia reqSend = new TLRPC.TL_messages_sendMedia();
                reqSend.peer = sendToPeer;
                reqSend.random_id = newMsg.random_id;
                if (type == 1) {
                    reqSend.media = new TLRPC.TL_inputMediaGeoPoint();
                    reqSend.media.geo_point = new TLRPC.TL_inputGeoPoint();
                    reqSend.media.geo_point.lat = lat;
                    reqSend.media.geo_point._long = lon;
                    performSendMessageRequest(reqSend, newMsgObj);
                } else if (type == 2) {
                    reqSend.media = new TLRPC.TL_inputMediaUploadedPhoto();
                    DelayedMessage delayedMessage = new DelayedMessage();
                    delayedMessage.sendRequest = reqSend;
                    delayedMessage.type = 0;
                    delayedMessage.obj = newMsgObj;
                    delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                    performSendDelayedMessage(delayedMessage);
                } else if (type == 3) {
                    reqSend.media = new TLRPC.TL_inputMediaUploadedThumbVideo();
                    reqSend.media.duration = video.duration;
                    reqSend.media.w = video.w;
                    reqSend.media.h = video.h;
                    DelayedMessage delayedMessage = new DelayedMessage();
                    delayedMessage.sendRequest = reqSend;
                    delayedMessage.type = 1;
                    delayedMessage.obj = newMsgObj;
                    delayedMessage.location = video.thumb.location;
                    delayedMessage.videoLocation = video;
                    performSendDelayedMessage(delayedMessage);
                } else if (type == 5) {
                    reqSend.media = new TLRPC.TL_inputMediaPhoto();
                    TLRPC.TL_inputPhoto ph = new TLRPC.TL_inputPhoto();
                    ph.id = msgObj.messageOwner.action.photo.id;
                    ph.access_hash = msgObj.messageOwner.action.photo.access_hash;
                    ((TLRPC.TL_inputMediaPhoto)reqSend.media).id = ph;
                    performSendMessageRequest(reqSend, newMsgObj);
                } else if (type == 6) {
                    reqSend.media = new TLRPC.TL_inputMediaContact();
                    reqSend.media.phone_number = user.phone;
                    reqSend.media.first_name = user.first_name;
                    reqSend.media.last_name = user.last_name;
                    performSendMessageRequest(reqSend, newMsgObj);
                }
            } else {
                TLRPC.TL_decryptedMessage reqSend = new TLRPC.TL_decryptedMessage();
                reqSend.random_id = newMsg.random_id;
                reqSend.random_bytes = new byte[Math.max(1, (int)Math.ceil(random.nextDouble() * 16))];
                random.nextBytes(reqSend.random_bytes);
                reqSend.message = "";
                if (type == 1) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaGeoPoint();
                    reqSend.media.lat = lat;
                    reqSend.media._long = lon;
                    performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null);
                } else if (type == 2) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaPhoto();
                    reqSend.media.iv = new byte[32];
                    reqSend.media.key = new byte[32];
                    random.nextBytes(reqSend.media.iv);
                    random.nextBytes(reqSend.media.key);
                    TLRPC.PhotoSize small = photo.sizes.get(0);
                    TLRPC.PhotoSize big = photo.sizes.get(photo.sizes.size() - 1);
                    reqSend.media.thumb = small.bytes;
                    reqSend.media.thumb_h = small.h;
                    reqSend.media.thumb_w = small.w;
                    reqSend.media.w = big.w;
                    reqSend.media.h = big.h;
                    reqSend.media.size = big.size;

                    DelayedMessage delayedMessage = new DelayedMessage();
                    delayedMessage.sendEncryptedRequest = reqSend;
                    delayedMessage.type = 0;
                    delayedMessage.obj = newMsgObj;
                    delayedMessage.encryptedChat = encryptedChat;
                    delayedMessage.location = photo.sizes.get(photo.sizes.size() - 1).location;
                    performSendDelayedMessage(delayedMessage);
                } else if (type == 3) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaVideo();
                    reqSend.media.iv = new byte[32];
                    reqSend.media.key = new byte[32];
                    random.nextBytes(reqSend.media.iv);
                    random.nextBytes(reqSend.media.key);
                    reqSend.media.duration = video.duration;
                    reqSend.media.size = video.size;
                    reqSend.media.w = video.w;
                    reqSend.media.h = video.h;
                    reqSend.media.thumb = video.thumb.bytes;
                    reqSend.media.thumb_h = video.thumb.h;
                    reqSend.media.thumb_w = video.thumb.w;

                    DelayedMessage delayedMessage = new DelayedMessage();
                    delayedMessage.sendEncryptedRequest = reqSend;
                    delayedMessage.type = 1;
                    delayedMessage.obj = newMsgObj;
                    delayedMessage.encryptedChat = encryptedChat;
                    delayedMessage.videoLocation = video;
                    performSendDelayedMessage(delayedMessage);
                } else if (type == 5) {

                } else if (type == 6) {
                    reqSend.media = new TLRPC.TL_decryptedMessageMediaContact();
                    reqSend.media.phone_number = user.phone;
                    reqSend.media.first_name = user.first_name;
                    reqSend.media.last_name = user.last_name;
                    reqSend.media.user_id = user.id;
                    performSendEncryptedRequest(reqSend, newMsgObj, encryptedChat, null);
                }
            }
        } else if (type == 4) {
            TLRPC.TL_messages_forwardMessage reqSend = new TLRPC.TL_messages_forwardMessage();
            reqSend.peer = sendToPeer;
            reqSend.random_id = newMsg.random_id;
            if (msgObj.messageOwner.id >= 0) {
                reqSend.id = msgObj.messageOwner.id;
            } else {
                reqSend.id = msgObj.messageOwner.fwd_msg_id;
            }
            performSendMessageRequest(reqSend, newMsgObj);
        }
    }

    private void processSendedMessage(TLRPC.Message newMsg, TLRPC.Message sendedMessage, TLRPC.EncryptedFile file, TLRPC.DecryptedMessage decryptedMessage) {
        if (sendedMessage != null) {
            if (sendedMessage.media instanceof TLRPC.TL_messageMediaPhoto && sendedMessage.media.photo != null && newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
                for (TLRPC.PhotoSize size : sendedMessage.media.photo.sizes) {
                    if (size instanceof TLRPC.TL_photoSizeEmpty) {
                        continue;
                    }
                    for (TLRPC.PhotoSize size2 : newMsg.media.photo.sizes) {
                        if (size.type.equals(size2.type)) {
                            String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                            String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                            if (fileName.equals(fileName2)) {
                                break;
                            }
                            File cacheFile = new File(Utilities.getCacheDir(), fileName + ".jpg");
                            File cacheFile2 = new File(Utilities.getCacheDir(), fileName2 + ".jpg");
                            cacheFile.renameTo(cacheFile2);
                            FileLoader.Instance.replaceImageInCache(fileName, fileName2);
                            size2.location = size.location;
                            break;
                        }
                    }
                }
                sendedMessage.message = newMsg.message;
                sendedMessage.attachPath = newMsg.attachPath;
            } else if (sendedMessage.media instanceof TLRPC.TL_messageMediaVideo && sendedMessage.media.video != null && newMsg.media instanceof TLRPC.TL_messageMediaVideo && newMsg.media.video != null) {
                TLRPC.PhotoSize size2 = newMsg.media.video.thumb;
                TLRPC.PhotoSize size = sendedMessage.media.video.thumb;
                if (size2.location != null && size.location != null && !(size instanceof TLRPC.TL_photoSizeEmpty) && !(size2 instanceof TLRPC.TL_photoSizeEmpty)) {
                    String fileName = size2.location.volume_id + "_" + size2.location.local_id;
                    String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                    if (fileName.equals(fileName2)) {
                        return;
                    }
                    File cacheFile = new File(Utilities.getCacheDir(), fileName + ".jpg");
                    File cacheFile2 = new File(Utilities.getCacheDir(), fileName2 + ".jpg");
                    boolean result = cacheFile.renameTo(cacheFile2);
                    FileLoader.Instance.replaceImageInCache(fileName, fileName2);
                    size2.location = size.location;
                    sendedMessage.message = newMsg.message;
                    sendedMessage.attachPath = newMsg.attachPath;
                }
            }
        } else if (file != null) {
            if (newMsg.media instanceof TLRPC.TL_messageMediaPhoto && newMsg.media.photo != null) {
                TLRPC.PhotoSize size = newMsg.media.photo.sizes.get(newMsg.media.photo.sizes.size() - 1);
                String fileName = size.location.volume_id + "_" + size.location.local_id;
                size.location = new TLRPC.TL_fileEncryptedLocation();
                size.location.key = decryptedMessage.media.key;
                size.location.iv = decryptedMessage.media.iv;
                size.location.dc_id = file.dc_id;
                size.location.volume_id = file.id;
                size.location.secret = file.access_hash;
                size.location.local_id = file.key_fingerprint;
                String fileName2 = size.location.volume_id + "_" + size.location.local_id;
                File cacheFile = new File(Utilities.getCacheDir(), fileName + ".jpg");
                File cacheFile2 = new File(Utilities.getCacheDir(), fileName2 + ".jpg");
                boolean result = cacheFile.renameTo(cacheFile2);
                FileLoader.Instance.replaceImageInCache(fileName, fileName2);
                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.Instance.putMessages(arr, false, true);
            } else if (newMsg.media instanceof TLRPC.TL_messageMediaVideo && newMsg.media.video != null) {
                TLRPC.Video video = newMsg.media.video;
                newMsg.media.video = new TLRPC.TL_videoEncrypted();
                newMsg.media.video.duration = video.duration;
                newMsg.media.video.thumb = video.thumb;
                newMsg.media.video.id = video.id;
                newMsg.media.video.dc_id = file.dc_id;
                newMsg.media.video.w = video.w;
                newMsg.media.video.h = video.h;
                newMsg.media.video.date = video.date;
                newMsg.media.video.caption = "";
                newMsg.media.video.user_id = video.user_id;
                newMsg.media.video.size = file.size;
                newMsg.media.video.id = file.id;
                newMsg.media.video.access_hash = file.access_hash;
                newMsg.media.video.key = decryptedMessage.media.key;
                newMsg.media.video.iv = decryptedMessage.media.iv;
                newMsg.media.video.path = video.path;
                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(newMsg);
                MessagesStorage.Instance.putMessages(arr, false, true);
            }
        }
    }

    private void performSendEncryptedRequest(final TLRPC.DecryptedMessage req, final MessageObject newMsgObj, final TLRPC.EncryptedChat chat, final TLRPC.InputEncryptedFile encryptedFile) {
        if (req == null) {
            return;
        }
        //TLRPC.decryptedMessageLayer messageLayer = new TLRPC.decryptedMessageLayer();
        //messageLayer.layer = 8;
        //messageLayer.message = req;
        SerializedData data = new SerializedData();
        req.serializeToStream(data);

        SerializedData toEncrypt = new SerializedData();
        toEncrypt.writeInt32(data.length());
        toEncrypt.writeRaw(data.toByteArray());

        byte[] innerData = toEncrypt.toByteArray();

        byte[] messageKeyFull = Utilities.computeSHA1(innerData);
        byte[] messageKey = new byte[16];
        System.arraycopy(messageKeyFull, messageKeyFull.length - 16, messageKey, 0, 16);

        MessageKeyData keyData = Utilities.generateMessageKeyData(chat.auth_key, messageKey, false);

        SerializedData dataForEncryption = new SerializedData();
        dataForEncryption.writeRaw(innerData);
        byte[] b = new byte[1];
        while (dataForEncryption.length() % 16 != 0) {
            MessagesController.random.nextBytes(b);
            dataForEncryption.writeByte(b[0]);
        }

        byte[] encryptedData = Utilities.aesIgeEncryption(dataForEncryption.toByteArray(), keyData.aesKey, keyData.aesIv, true, false);

        data = new SerializedData();
        data.writeInt64(chat.key_fingerprint);
        data.writeRaw(messageKey);
        data.writeRaw(encryptedData);

        TLObject reqToSend;

        if (encryptedFile == null) {
            TLRPC.TL_messages_sendEncrypted req2 = new TLRPC.TL_messages_sendEncrypted();
            req2.data = data.toByteArray();
            req2.random_id = req.random_id;
            req2.peer = new TLRPC.TL_inputEncryptedChat();
            req2.peer.chat_id = chat.id;
            req2.peer.access_hash = chat.access_hash;
            reqToSend = req2;
        } else {
            TLRPC.TL_messages_sendEncryptedFile req2 = new TLRPC.TL_messages_sendEncryptedFile();
            req2.data = data.toByteArray();
            req2.random_id = req.random_id;
            req2.peer = new TLRPC.TL_inputEncryptedChat();
            req2.peer.chat_id = chat.id;
            req2.peer.access_hash = chat.access_hash;
            req2.file = encryptedFile;
            reqToSend = req2;
        }
        ConnectionsManager.Instance.performRpc(reqToSend, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_SentEncryptedMessage res = (TLRPC.messages_SentEncryptedMessage)response;
                    newMsgObj.messageOwner.date = res.date;
                    if (res.file instanceof TLRPC.TL_encryptedFile) {
                        processSendedMessage(newMsgObj.messageOwner, null, res.file, req);
                    }
                    MessagesStorage.Instance.updateMessageStateAndId(newMsgObj.messageOwner.random_id, newMsgObj.messageOwner.id, newMsgObj.messageOwner.id, res.date, true);
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENT;
                            NotificationCenter.Instance.postNotificationName(messageReceivedByServer, newMsgObj.messageOwner.id, newMsgObj.messageOwner.id);
                            sendingMessages.remove(newMsgObj.messageOwner.id);
                        }
                    });
                } else {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            sendingMessages.remove(newMsgObj.messageOwner.id);
                            newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SEND_ERROR;
                            NotificationCenter.Instance.postNotificationName(messageSendError, newMsgObj.messageOwner.id);
                        }
                    });
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    private void performSendMessageRequest(TLObject req, final MessageObject newMsgObj) {
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    final int oldId = newMsgObj.messageOwner.id;
                    ArrayList<TLRPC.Message> sendedMessages = new ArrayList<TLRPC.Message>();

                    if (response instanceof TLRPC.TL_messages_sentMessage) {
                        TLRPC.TL_messages_sentMessage res = (TLRPC.TL_messages_sentMessage)response;
                        newMsgObj.messageOwner.id = res.id;
                        if(MessagesStorage.lastSeqValue + 1 == res.seq) {
                            MessagesStorage.lastSeqValue = res.seq;
                            MessagesStorage.lastDateValue = res.date;
                            MessagesStorage.lastPtsValue = res.pts;
                            MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                        } else {
                            getDifference();
                        }
                    } else if (response instanceof TLRPC.messages_StatedMessage) {
                        TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage)response;
                        sendedMessages.add(res.message);
                        newMsgObj.messageOwner.id = res.message.id;
                        processSendedMessage(newMsgObj.messageOwner, res.message, null, null);
                        if(MessagesStorage.lastSeqValue + 1 == res.seq) {
                            MessagesStorage.lastSeqValue = res.seq;
                            MessagesStorage.lastPtsValue = res.pts;
                            MessagesStorage.lastDateValue = res.message.date;
                            MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                        } else {
                            getDifference();
                        }
                    } else if (response instanceof TLRPC.messages_StatedMessages) {
                        TLRPC.messages_StatedMessages res = (TLRPC.messages_StatedMessages)response;
                        if (!res.messages.isEmpty()) {
                            TLRPC.Message message = res.messages.get(0);
                            newMsgObj.messageOwner.id = message.id;
                            sendedMessages.add(message);
                            processSendedMessage(newMsgObj.messageOwner, message, null, null);
                        }
                        if(MessagesStorage.lastSeqValue + 1 == res.seq) {
                            MessagesStorage.lastSeqValue = res.seq;
                            MessagesStorage.lastPtsValue = res.pts;
                            MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                        } else {
                            getDifference();
                        }
                    }
                    MessagesStorage.Instance.updateMessageStateAndId(newMsgObj.messageOwner.random_id, oldId, newMsgObj.messageOwner.id, 0, true);
                    if (!sendedMessages.isEmpty()) {
                        MessagesStorage.Instance.putMessages(sendedMessages, true, true);
                    }
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENT;
                            NotificationCenter.Instance.postNotificationName(messageReceivedByServer, oldId, newMsgObj.messageOwner.id);
                            sendingMessages.remove(oldId);
                        }
                    });
                } else {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            sendingMessages.remove(newMsgObj.messageOwner.id);
                            newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SEND_ERROR;
                            NotificationCenter.Instance.postNotificationName(messageSendError, newMsgObj.messageOwner.id);
                        }
                    });
                }
            }
        }, null, (req instanceof TLRPC.TL_messages_forwardMessages ? null : new RPCRequest.RPCQuickAckDelegate() {
            @Override
            public void quickAck() {
                final int msg_id = newMsgObj.messageOwner.id;
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        newMsgObj.messageOwner.send_state = MESSAGE_SEND_STATE_SENT;
                        NotificationCenter.Instance.postNotificationName(messageReceivedByAck, msg_id);
                    }
                });
            }
        }), true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors, ConnectionsManager.DEFAULT_DATACENTER_ID);
    }

    private void performSendDelayedMessage(final DelayedMessage message) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (message.type == 0) {
                    String location = Utilities.getCacheDir() + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                    delayedMessages.put(location, message);
                    if (message.sendRequest != null) {
                        FileLoader.Instance.uploadFile(location, null, null);
                    } else {
                        FileLoader.Instance.uploadFile(location, message.sendEncryptedRequest.media.key, message.sendEncryptedRequest.media.iv);
                    }
                } else if (message.type == 1) {
                    if (message.sendRequest != null) {
                        if (message.sendRequest.media.thumb == null) {
                            String location = Utilities.getCacheDir() + "/" + message.location.volume_id + "_" + message.location.local_id + ".jpg";
                            delayedMessages.put(location, message);
                            FileLoader.Instance.uploadFile(location, null, null);
                        } else {
                            String location = message.videoLocation.path;
                            if (location == null) {
                                location = Utilities.getCacheDir() + "/" + message.videoLocation.id + ".mp4";
                            }
                            delayedMessages.put(location, message);
                            FileLoader.Instance.uploadFile(location, null, null);
                        }
                    } else {
                        String location = message.videoLocation.path;
                        if (location == null) {
                            location = Utilities.getCacheDir() + "/" + message.videoLocation.id + ".mp4";
                        }
                        delayedMessages.put(location, message);
                        FileLoader.Instance.uploadFile(location, message.sendEncryptedRequest.media.key, message.sendEncryptedRequest.media.iv);
                    }
                }
            }
        });
    }

    public void fileDidFailedUpload(final String location) {
        if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
            uploadingAvatar = null;
        } else {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    DelayedMessage obj = delayedMessages.get(location);
                    if (obj != null) {
                        obj.obj.messageOwner.send_state = MESSAGE_SEND_STATE_SEND_ERROR;
                        sendingMessages.remove(obj.obj.messageOwner.id);
                        NotificationCenter.Instance.postNotificationName(messageSendError, obj.obj.messageOwner.id);
                        delayedMessages.remove(location);
                    }
                }
            });
        }
    }

    public void rebuildContactsWithNewUser(final TLRPC.User user, final long bookId) {
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                final HashMap<Integer, Contact> contactsMapBook = new HashMap<Integer, Contact>(contactsBook);
                final HashMap<String, ArrayList<Contact>> sectionsDictBook = new HashMap<String, ArrayList<Contact>>();
                final ArrayList<String> sortedSectionsArrayBook = new ArrayList<String>();
                Contact newContactBook = new Contact();
                newContactBook.first_name = user.first_name;
                newContactBook.last_name = user.last_name;
                newContactBook.id = (int)bookId;
                newContactBook.phones = new ArrayList<String>();
                newContactBook.phones.add(user.phone);
                newContactBook.phoneTypes = new ArrayList<String>();
                newContactBook.phoneTypes.add(ApplicationLoader.applicationContext.getString(R.string.PhoneMobile));
                contactsMapBook.put((int)bookId, newContactBook);

                String contactsImportHash = "";
                MessageDigest mdEnc = null;
                try {
                    mdEnc = MessageDigest.getInstance("MD5");
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                for (HashMap.Entry<Integer, Contact> pair : contactsMapBook.entrySet()) {
                    Contact value = pair.getValue();
                    int id = pair.getKey();

                    if (mdEnc != null) {
                        for (int a = 0; a < value.phones.size(); a++) {
                            String str = id + value.first_name + value.last_name + value.phones.get(a);
                            mdEnc.update(str.getBytes());
                        }
                    }

                    String key = value.last_name;
                    if (key.length() == 0) {
                        key = value.first_name;
                    }
                    if (key.length() == 0) {
                        key = "#";
                        if (value.phones.size() != 0) {
                            value.first_name = "+" + value.phones.get(0);
                        }
                    } else {
                        key = key.toUpperCase();
                    }
                    if (key.length() > 1) {
                        key = key.substring(0, 1);
                    }
                    ArrayList<Contact> arr = sectionsDictBook.get(key);
                    if (arr == null) {
                        arr = new ArrayList<Contact>();
                        sectionsDictBook.put(key, arr);
                        sortedSectionsArrayBook.add(key);
                    }
                    arr.add(value);
                }
                for (HashMap.Entry<String, ArrayList<Contact>> entry : sectionsDictBook.entrySet()) {
                    Collections.sort(entry.getValue(), new Comparator<Contact>() {
                        @Override
                        public int compare(Contact contact, Contact contact2) {
                            String toComapre1 = contact.first_name;
                            if (toComapre1.length() == 0) {
                                toComapre1 = contact.last_name;
                            }
                            String toComapre2 = contact2.first_name;
                            if (toComapre2.length() == 0) {
                                toComapre2 = contact2.last_name;
                            }
                            return toComapre1.compareTo(toComapre2);
                        }
                    });
                }
                Collections.sort(sortedSectionsArrayBook, new Comparator<String>() {
                    @Override
                    public int compare(String s, String s2) {
                        char cv1 = s.charAt(0);
                        char cv2 = s2.charAt(0);
                        if (cv1 == '#') {
                            return 1;
                        } else if (cv2 == '#') {
                            return -1;
                        }/* else if (cv1 >= 'A' && cv1 <= 'Z' && cv2 >= 'A' && cv2 <= 'Z') {
                            return s.compareTo(s2);
                        } else if (cv1 >= 'A' && cv1 <= 'Z' && !(cv2 >= 'A' && cv2 <= 'Z')) {
                            return 1;
                        } else if (!(cv1 >= 'A' && cv1 <= 'Z') && cv2 >= 'A' && cv2 <= 'Z') {
                            return -1;
                        }*/
                        return s.compareTo(s2);
                    }
                });
                String importHash = String.format(Locale.US, "%32s", new BigInteger(1, mdEnc.digest()).toString(16)).replace(' ', '0');
                //end of phone book update


                final ArrayList<TLRPC.TL_contact> contactsArr = new ArrayList<TLRPC.TL_contact>(contacts);
                TLRPC.TL_contact newContact = new TLRPC.TL_contact();
                newContact.user_id = user.id;
                contactsArr.add(newContact);
                Collections.sort(contactsArr, new Comparator<TLRPC.TL_contact>() {
                    @Override
                    public int compare(TLRPC.TL_contact tl_contact, TLRPC.TL_contact tl_contact2) {
                        TLRPC.User user1 = users.get(tl_contact.user_id);
                        TLRPC.User user2 = users.get(tl_contact2.user_id);
                        String name1 = user1.first_name;
                        if (name1 == null || name1.length() == 0) {
                            name1 = user1.last_name;
                        }
                        String name2 = user2.first_name;
                        if (name2 == null || name2.length() == 0) {
                            name2 = user2.last_name;
                        }
                        return name1.compareTo(name2);
                    }
                });

                final HashMap<String, ArrayList<TLRPC.TL_contact>> sectionsDict = new HashMap<String, ArrayList<TLRPC.TL_contact>>();
                final SparseArray<TLRPC.TL_contact> contactsDictionery = new SparseArray<TLRPC.TL_contact>();
                final ArrayList<String> sortedSectionsArray = new ArrayList<String>();
                final HashMap<String, TLRPC.TL_contact> contactsPhones = new HashMap<String, TLRPC.TL_contact>();

                for (TLRPC.TL_contact value : contactsArr) {
                    TLRPC.User user = users.get(value.user_id);
                    contactsDictionery.put(value.user_id, value);
                    contactsPhones.put(user.phone, value);

                    String key = user.last_name;
                    if (key == null || key.length() == 0) {
                        key = user.first_name;
                    }
                    if (key.length() == 0) {
                        key = "#";
                    } else {
                        key = key.toUpperCase();
                    }
                    if (key.length() > 1) {
                        key = key.substring(0, 1);
                    }
                    ArrayList<TLRPC.TL_contact> arr = sectionsDict.get(key);
                    if (arr == null) {
                        arr = new ArrayList<TLRPC.TL_contact>();
                        sectionsDict.put(key, arr);
                        sortedSectionsArray.add(key);
                    }
                    arr.add(value);
                }
                for (HashMap.Entry<String, ArrayList<TLRPC.TL_contact>> entry : sectionsDict.entrySet()) {
                    Collections.sort(entry.getValue(), new Comparator<TLRPC.TL_contact>() {
                        @Override
                        public int compare(TLRPC.TL_contact contact, TLRPC.TL_contact contact2) {
                            TLRPC.User user1 = users.get(contact.user_id);
                            TLRPC.User user2 = users.get(contact2.user_id);
                            String toComapre1 = user1.first_name;
                            if (toComapre1 == null || toComapre1.length() == 0) {
                                toComapre1 = user1.last_name;
                            }
                            String toComapre2 = user2.first_name;
                            if (toComapre2 == null || toComapre2.length() == 0) {
                                toComapre2 = user2.last_name;
                            }
                            return toComapre1.compareTo(toComapre2);
                        }
                    });
                }
                Collections.sort(sortedSectionsArray, new Comparator<String>() {
                    @Override
                    public int compare(String s, String s2) {
                        char cv1 = s.charAt(0);
                        char cv2 = s2.charAt(0);
                        if (cv1 == '#') {
                            return 1;
                        } else if (cv2 == '#') {
                            return -1;
                        }
//                        } else if (cv1 >= 'A' && cv1 <= 'Z' && cv2 >= 'A' && cv2 <= 'Z') {
//                            return s.compareTo(s2);
//                        } else if (cv1 >= 'A' && cv1 <= 'Z' && !(cv2 >= 'A' && cv2 <= 'Z')) {
//                            return 1;
//                        } else if (!(cv1 >= 'A' && cv1 <= 'Z') && cv2 >= 'A' && cv2 <= 'Z') {
//                            return -1;
//                        }
                        return s.compareTo(s2);
                    }
                });

                final ArrayList<TLRPC.TL_contact> contactsArrSortedByIds = new ArrayList<TLRPC.TL_contact>(contactsArr);
                Collections.sort(contactsArrSortedByIds, new Comparator<TLRPC.TL_contact>() {
                    @Override
                    public int compare(TLRPC.TL_contact lhs, TLRPC.TL_contact rhs) {
                        Integer lid = lhs.user_id;
                        Integer rid = rhs.user_id;
                        return lid.compareTo(rid);
                    }
                });

                String ids = "";
                for (TLRPC.TL_contact aContactsArr : contactsArrSortedByIds) {
                    if (ids.length() != 0) {
                        ids += ",";
                    }
                    ids += aContactsArr.user_id;
                }
                UserConfig.contactsHash = Utilities.MD5(ids);
                UserConfig.importHash = importHash;
                UserConfig.saveConfig(false);

                MessagesStorage.Instance.putContacts(contactsArr, true);
                ArrayList<TLRPC.User> users = new ArrayList<TLRPC.User>();
                users.add(user);
                MessagesStorage.Instance.putUsersAndChats(users, null, true, true);

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        contactsBook = contactsMapBook;
                        contactsSectionsDict = sectionsDictBook;
                        sortedContactsSectionsArray = sortedSectionsArrayBook;
                        NotificationCenter.Instance.postNotificationName(contactsBookDidLoaded);
                        contacts = contactsArr;
                        contactsByPhones = contactsPhones;
                        contactsDict = contactsDictionery;
                        usersSectionsDict = sectionsDict;
                        sortedUsersSectionsArray = sortedSectionsArray;
                        NotificationCenter.Instance.postNotificationName(contactsDidLoaded);
                        NotificationCenter.Instance.postNotificationName(updateInterfaces, UPDATE_MASK_ALL);
                    }
                });
            }
        });
    }

    public void addContact(TLRPC.User user) {
        if (user == null) {
            return;
        }
        long num = addContact(currentAccount, user, user.phone);
        rebuildContactsWithNewUser(user, num);

        TLRPC.TL_contacts_importContacts req = new TLRPC.TL_contacts_importContacts();
        ArrayList<TLRPC.TL_inputPhoneContact> contacts = new ArrayList<TLRPC.TL_inputPhoneContact>();
        TLRPC.TL_inputPhoneContact c = new TLRPC.TL_inputPhoneContact();
        c.phone = user.phone;
        c.first_name = user.first_name;
        c.last_name = user.last_name;
        c.client_id = num;
        contacts.add(c);
        req.contacts = contacts;
        req.replace = false;
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }

    public void fileDidUploaded(final String location, final TLRPC.InputFile file, final TLRPC.InputEncryptedFile encryptedFile) {
        if (uploadingAvatar != null && uploadingAvatar.equals(location)) {
            TLRPC.TL_photos_uploadProfilePhoto req = new TLRPC.TL_photos_uploadProfilePhoto();
            req.caption = "";
            req.crop = new TLRPC.TL_inputPhotoCropAuto();
            req.file = file;
            req.geo_point = new TLRPC.TL_inputGeoPointEmpty();
            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
        } else {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    DelayedMessage message = delayedMessages.get(location);
                    if (message != null) {
                        if (file != null) {
                            if (message.type == 0) {
                                message.sendRequest.media.file = file;
                                performSendMessageRequest(message.sendRequest, message.obj);
                            } else if (message.type == 1) {
                                if (message.sendRequest.media.thumb == null) {
                                    message.sendRequest.media.thumb = file;
                                    performSendDelayedMessage(message);
                                } else {
                                    message.sendRequest.media.file = file;
                                    performSendMessageRequest(message.sendRequest, message.obj);
                                }
                            }
                        } else if (encryptedFile != null) {
                            if (message.type == 0) {
                                performSendEncryptedRequest(message.sendEncryptedRequest, message.obj, message.encryptedChat, encryptedFile);
                            } else if (message.type == 1) {
                                performSendEncryptedRequest(message.sendEncryptedRequest, message.obj, message.encryptedChat, encryptedFile);
                            }
                        }
                        delayedMessages.remove(location);
                    }
                }
            });
        }
    }

    public void createChat(String title, ArrayList<Integer> selectedContacts, final TLRPC.InputFile uploadedAvatar) {
        TLRPC.TL_messages_createChat req = new TLRPC.TL_messages_createChat();
        req.title = title;
        for (Integer uid : selectedContacts) {
            TLRPC.User user = users.get(uid);
            TLRPC.InputUser inputUser;
            if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                inputUser = new TLRPC.TL_inputUserForeign();
                inputUser.user_id = user.id;
                inputUser.access_hash = user.access_hash;
            } else {
                inputUser = new TLRPC.TL_inputUserContact();
                inputUser.user_id = user.id;
            }
            req.users.add(inputUser);
        }
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            NotificationCenter.Instance.postNotificationName(chatDidFailCreate);
                        }
                    });
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage)response;
                MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, true, true);
                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.Instance.putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    getDifference();
                }
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.clientUserId) {
                                UserConfig.currentUser = user;
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.Instance.postNotificationName(chatDidCreated, chat.id);
                        if (uploadedAvatar != null) {
                            changeChatAvatar(chat.id, uploadedAvatar);
                        }
                    }
                });

            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void addUserToChat(int chat_id, final int user_id, final TLRPC.ChatParticipants info) {
        TLRPC.TL_messages_addChatUser req = new TLRPC.TL_messages_addChatUser();
        req.chat_id = chat_id;
        req.fwd_limit = 50;
        TLRPC.User user = users.get(user_id);
        if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
            req.user_id = new TLRPC.TL_inputUserForeign();
            req.user_id.user_id = user.id;
            req.user_id.access_hash = user.access_hash;
        } else {
            req.user_id = new TLRPC.TL_inputUserContact();
            req.user_id.user_id = user.id;
        }
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }

                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage)response;
                MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, true, true);
                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.Instance.putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    getDifference();
                }

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.clientUserId) {
                                UserConfig.currentUser = user;
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        chats.put(chat.id, chat);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.Instance.postNotificationName(updateInterfaces, UPDATE_MASK_ALL);

                        if (info != null) {
                            for (TLRPC.TL_chatParticipant p : info.participants) {
                                if (p.user_id == user_id) {
                                    return;
                                }
                            }
                            TLRPC.TL_chatParticipant newPart = new TLRPC.TL_chatParticipant();
                            newPart.user_id = user_id;
                            newPart.inviter_id = UserConfig.clientUserId;
                            newPart.date = ConnectionsManager.Instance.getCurrentTime();
                            info.participants.add(0, newPart);
                            MessagesStorage.Instance.updateChatInfo(info.chat_id, info, true);
                            NotificationCenter.Instance.postNotificationName(chatInfoDidLoaded, info.chat_id, info);
                        }
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void deleteUserFromChat(int chat_id, final int user_id, final TLRPC.ChatParticipants info) {
        TLRPC.TL_messages_deleteChatUser req = new TLRPC.TL_messages_deleteChatUser();
        req.chat_id = chat_id;
        TLRPC.User user = users.get(user_id);
        if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
            req.user_id = new TLRPC.TL_inputUserForeign();
            req.user_id.user_id = user.id;
            req.user_id.access_hash = user.access_hash;
        } else if (user instanceof TLRPC.TL_userSelf) {
            req.user_id = new TLRPC.TL_inputUserSelf();
        } else {
            req.user_id = new TLRPC.TL_inputUserContact();
            req.user_id.user_id = user.id;
        }
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage)response;
                MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, true, true);
                if (user_id != UserConfig.clientUserId) {
                    final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                    messages.add(res.message);
                    MessagesStorage.Instance.putMessages(messages, true, true);
                }
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    getDifference();
                }

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.clientUserId) {
                                UserConfig.currentUser = user;
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        if (user_id != UserConfig.clientUserId) {
                            final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                            messagesObj.add(new MessageObject(res.message, users));
                            TLRPC.Chat chat = res.chats.get(0);
                            chats.put(chat.id, chat);
                            updateInterfaceWithMessages(-chat.id, messagesObj);
                            NotificationCenter.Instance.postNotificationName(updateInterfaces, UPDATE_MASK_ALL);
                        }
                        boolean changed = false;
                        if (info != null) {
                            for (int a = 0; a < info.participants.size(); a++) {
                                TLRPC.TL_chatParticipant p = info.participants.get(a);
                                if (p.user_id == user_id) {
                                    info.participants.remove(a);
                                    changed = true;
                                    break;
                                }
                            }
                            if (changed) {
                                MessagesStorage.Instance.updateChatInfo(info.chat_id, info, true);
                                NotificationCenter.Instance.postNotificationName(chatInfoDidLoaded, info.chat_id, info);
                            }
                        }
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void changeChatTitle(int chat_id, String title) {
        TLRPC.TL_messages_editChatTitle req = new TLRPC.TL_messages_editChatTitle();
        req.chat_id = chat_id;
        req.title = title;
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage)response;
                MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, true, true);
                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.Instance.putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    getDifference();
                }

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.clientUserId) {
                                UserConfig.currentUser = user;
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        chats.put(chat.id, chat);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.Instance.postNotificationName(updateInterfaces, UPDATE_MASK_ALL);
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
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
        ConnectionsManager.Instance.performRpc(req2, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                final TLRPC.messages_StatedMessage res = (TLRPC.messages_StatedMessage)response;
                MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, true, true);
                final ArrayList<TLRPC.Message> messages = new ArrayList<TLRPC.Message>();
                messages.add(res.message);
                MessagesStorage.Instance.putMessages(messages, true, true);
                if (MessagesStorage.lastSeqValue + 1 == res.seq) {
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else if (MessagesStorage.lastSeqValue != res.seq) {
                    getDifference();
                }

                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        for (TLRPC.User user : res.users) {
                            users.put(user.id, user);
                            if (user.id == UserConfig.clientUserId) {
                                UserConfig.currentUser = user;
                            }
                        }
                        for (TLRPC.Chat chat : res.chats) {
                            chats.put(chat.id, chat);
                        }
                        final ArrayList<MessageObject> messagesObj = new ArrayList<MessageObject>();
                        messagesObj.add(new MessageObject(res.message, users));
                        TLRPC.Chat chat = res.chats.get(0);
                        chats.put(chat.id, chat);
                        updateInterfaceWithMessages(-chat.id, messagesObj);
                        NotificationCenter.Instance.postNotificationName(updateInterfaces, UPDATE_MASK_ALL);
                    }
                });
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void unregistedPush() {
        if (!UserConfig.registeredForPush || UserConfig.pushString.length() == 0) {
            return;
        }
        TLRPC.TL_account_unregisterDevice req = new TLRPC.TL_account_unregisterDevice();
        req.token = UserConfig.pushString;
        req.token_type = 2;
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        },  null, true, RPCRequest.RPCRequestClassGeneric);

        TLRPC.TL_auth_logOut req2 = new TLRPC.TL_auth_logOut();
        ConnectionsManager.Instance.performRpc(req2, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        },  null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void registerForPush(final String regid) {
        if (regid == null || regid.length() == 0 || registeringForPush || UserConfig.clientUserId == 0) {
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
            req.lang_code = Locale.getDefault().getCountry();
            req.device_model = Build.MANUFACTURER + Build.MODEL;
            if (req.device_model == null) {
                req.device_model = "Android unknown";
            }
            req.system_version = "SDK " + Build.VERSION.SDK_INT;
            PackageInfo pInfo = ApplicationLoader.applicationContext.getPackageManager().getPackageInfo(ApplicationLoader.applicationContext.getPackageName(), 0);
            req.app_version = pInfo.versionName;
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
        if (req.app_version != null) {
            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        FileLog.e("tmessages", "registered for push");
                        UserConfig.registeredForPush = true;
                        UserConfig.pushString = regid;
                        UserConfig.saveConfig(false);
                    }
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            registeringForPush = false;
                        }
                    });
                }
            },  null, true, RPCRequest.RPCRequestClassGeneric);
        }
    }

    public void loadCurrentState() {
        if (updatingState) {
            return;
        }
        updatingState = true;
        TLRPC.TL_updates_getState req = new TLRPC.TL_updates_getState();
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                updatingState = false;
                if (error == null) {
                    TLRPC.TL_updates_state res = (TLRPC.TL_updates_state)response;
                    MessagesStorage.lastDateValue = res.date;
                    MessagesStorage.lastPtsValue = res.pts;
                    MessagesStorage.lastSeqValue = res.seq;
                    MessagesStorage.lastQtsValue = res.qts;
                    MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                } else {
                    loadCurrentState();
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
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
        if (ConnectionsManager.Instance.connectionState == 0) {
            ConnectionsManager.Instance.connectionState = 3;
            final int stateCopy = ConnectionsManager.Instance.connectionState;
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    NotificationCenter.Instance.postNotificationName(703, stateCopy);
                }
            });
        }
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                gettingDifference = false;
                gettingDifferenceAgain = false;
                if (error == null) {
                    final TLRPC.updates_Difference res = (TLRPC.updates_Difference)response;
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
                                msgUpdates.add((TLRPC.TL_updateMessageID)upd);
                                res.other_updates.remove(a);
                                a--;
                            }
                        }
                    }

                    MessagesStorage.Instance.storageQueue.postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            if (!msgUpdates.isEmpty()) {
                                final HashMap<Integer, Integer> corrected = new HashMap<Integer, Integer>();
                                for (TLRPC.TL_updateMessageID update : msgUpdates) {
                                    Integer oldId = MessagesStorage.Instance.updateMessageStateAndId(update.random_id, null, update.id, 0, false);
                                    if (oldId != null) {
                                        corrected.put(oldId, update.id);
                                    }
                                }

                                if (!corrected.isEmpty()) {
                                    Utilities.RunOnUIThread(new Runnable() {
                                        @Override
                                        public void run() {
                                            for (HashMap.Entry<Integer, Integer> entry : corrected.entrySet()) {
                                                Integer oldId = entry.getKey();
                                                sendingMessages.remove(oldId);
                                                Integer newId = entry.getValue();
                                                NotificationCenter.Instance.postNotificationName(messageReceivedByServer, oldId, newId);
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
                                            TLRPC.Message message = decryptMessage(encryptedMessage);
                                            if (message != null) {
                                                res.new_messages.add(message);
                                            }
                                        }

                                        MessageObject lastMessage = null;
                                        for (TLRPC.Message message : res.new_messages) {
                                            MessageObject obj = new MessageObject(message, usersDict);

                                            long dialog_id = obj.messageOwner.dialog_id;
                                            if (dialog_id == 0) {
                                                if (obj.messageOwner.to_id.chat_id != 0) {
                                                    dialog_id = -obj.messageOwner.to_id.chat_id;
                                                } else {
                                                    dialog_id = obj.messageOwner.to_id.user_id;
                                                }
                                            }

                                            if ((dialog_id != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) && !obj.messageOwner.out && (lastMessage == null || lastMessage.messageOwner.date < obj.messageOwner.date)) {
                                                lastMessage = obj;
                                            }
                                            long uid;
                                            if (message.dialog_id != 0) {
                                                uid = message.dialog_id;
                                            } else {
                                                if (message.to_id.chat_id != 0) {
                                                    uid = -message.to_id.chat_id;
                                                } else {
                                                    if (message.to_id.user_id == UserConfig.clientUserId) {
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
                                        MessagesStorage.Instance.storageQueue.postRunnable(new Runnable() {
                                            @Override
                                            public void run() {
                                                MessagesStorage.Instance.startTransaction(false);
                                                MessagesStorage.Instance.putMessages(res.new_messages, false, false);
                                                MessagesStorage.Instance.putUsersAndChats(res.users, res.chats, false, false);
                                                MessagesStorage.Instance.commitTransaction(false);
                                            }
                                        });

                                        final MessageObject object = lastMessage;
                                        Utilities.RunOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                for (TLRPC.User user : res.users) {
                                                    users.put(user.id, user);
                                                    if (user.id == UserConfig.clientUserId) {
                                                        UserConfig.currentUser = user;
                                                    }
                                                }
                                                for (TLRPC.Chat chat : res.chats) {
                                                    chats.put(chat.id, chat);
                                                }
                                                for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : messages.entrySet()) {
                                                    Long key = pair.getKey();
                                                    ArrayList<MessageObject> value = pair.getValue();
                                                    updateInterfaceWithMessages(key, value);
                                                }
                                                NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                                                if (object != null) {
                                                    showInAppNotification(object);
                                                }
                                            }
                                        });
                                    }

                                    if (res != null && !res.other_updates.isEmpty()) {
                                        processUpdateArray(res.other_updates, res.users, res.chats);
                                    }

                                    if (res instanceof TLRPC.TL_updates_difference) {
                                        MessagesStorage.lastSeqValue = res.state.seq;
                                        MessagesStorage.lastDateValue = res.state.date;
                                        MessagesStorage.lastPtsValue = res.state.pts;
                                        MessagesStorage.lastQtsValue = res.state.qts;
                                        ConnectionsManager.Instance.connectionState = 0;
                                        final int stateCopy = ConnectionsManager.Instance.connectionState;
                                        Utilities.RunOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                NotificationCenter.Instance.postNotificationName(703, stateCopy);
                                            }
                                        });
                                    } else if (res instanceof TLRPC.TL_updates_differenceSlice) {
                                        MessagesStorage.lastSeqValue = res.intermediate_state.seq;
                                        MessagesStorage.lastDateValue = res.intermediate_state.date;
                                        MessagesStorage.lastPtsValue = res.intermediate_state.pts;
                                        MessagesStorage.lastQtsValue = res.intermediate_state.qts;
                                        gettingDifferenceAgain = true;
                                        getDifference();
                                    } else if (res instanceof TLRPC.TL_updates_differenceEmpty) {
                                        MessagesStorage.lastSeqValue = res.seq;
                                        MessagesStorage.lastDateValue = res.date;
                                        ConnectionsManager.Instance.connectionState = 0;
                                        final int stateCopy = ConnectionsManager.Instance.connectionState;
                                        Utilities.RunOnUIThread(new Runnable() {
                                            @Override
                                            public void run() {
                                                NotificationCenter.Instance.postNotificationName(703, stateCopy);
                                            }
                                        });
                                    }
                                    MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
                                }
                            });
                        }
                    });

                    FileLog.e("tmessages", "received defference with date = " + MessagesStorage.lastDateValue + " pts = " + MessagesStorage.lastPtsValue + " seq = " + MessagesStorage.lastSeqValue);
                    FileLog.e("tmessages", "messages = " + res.new_messages.size() + " users = " + res.users.size() + " chats = " + res.chats.size() + " other updates = " + res.other_updates.size());
                } else {
                    loadCurrentState();
                    FileLog.e("tmessages", "get difference error, don't know what to do :(");
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void processUpdates(final TLRPC.Updates updates) {
        boolean needGetDiff = false;
        boolean needReceivedQueue = false;
        if (updates instanceof TLRPC.TL_updateShort) {
            ArrayList<TLRPC.Update> arr = new ArrayList<TLRPC.Update>();
            arr.add(updates.update);
            //MessagesStorage.lastDateValue = updates.date;
            processUpdateArray(arr, null, null);
        } else if (updates instanceof TLRPC.TL_updateShortChatMessage) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq && chats.get(updates.chat_id) != null && users.get(updates.from_id) != null) {
                TLRPC.TL_message message = new TLRPC.TL_message();
                message.from_id = updates.from_id;
                message.id = updates.id;
                message.to_id = new TLRPC.TL_peerChat();
                message.to_id.chat_id = updates.chat_id;
                message.message = updates.message;
                message.date = updates.date;
                message.unread = true;
                message.media = new TLRPC.TL_messageMediaEmpty();
                MessagesStorage.lastSeqValue = updates.seq;
                MessagesStorage.lastPtsValue = updates.pts;
                final MessageObject obj = new MessageObject(message, null);
                final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
                objArr.add(obj);
                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(message);
                MessagesStorage.Instance.putMessages(arr, false, true);
                final boolean printUpdate = updatePrintingUsersWithNewMessages(-updates.chat_id, objArr);
                if (printUpdate) {
                    updatePrintingStrings();
                }
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (printUpdate) {
                            NotificationCenter.Instance.postNotificationName(userPrintUpdate, (long)(-updates.chat_id));
                            NotificationCenter.Instance.postNotificationName(userPrintUpdateAll);
                        }
                        if (obj.messageOwner.from_id != UserConfig.clientUserId) {
                            long dialog_id;
                            if (obj.messageOwner.to_id.chat_id != 0) {
                                dialog_id = -obj.messageOwner.to_id.chat_id;
                            } else {
                                dialog_id = obj.messageOwner.to_id.user_id;
                            }
                            if (dialog_id != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) {
                                showInAppNotification(obj);
                            }
                        }
                        updateInterfaceWithMessages(-updates.chat_id, objArr);
                        NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                    }
                });
            } else {
                FileLog.e("tmessages", "need get diff TL_updateShortChatMessage, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                needGetDiff = true;
            }
        } else if (updates instanceof TLRPC.TL_updateShortMessage) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq && users.get(updates.from_id) != null) {
                TLRPC.TL_message message = new TLRPC.TL_message();
                message.from_id = updates.from_id;
                message.id = updates.id;
                message.to_id = new TLRPC.TL_peerUser();
                message.to_id.user_id = updates.from_id;
                message.message = updates.message;
                message.date = updates.date;
                message.unread = true;
                message.media = new TLRPC.TL_messageMediaEmpty();
                MessagesStorage.lastSeqValue = updates.seq;
                MessagesStorage.lastPtsValue = updates.pts;
                MessagesStorage.lastDateValue = updates.date;
                final MessageObject obj = new MessageObject(message, null);
                final ArrayList<MessageObject> objArr = new ArrayList<MessageObject>();
                objArr.add(obj);
                ArrayList<TLRPC.Message> arr = new ArrayList<TLRPC.Message>();
                arr.add(message);
                MessagesStorage.Instance.putMessages(arr, false, true);
                final boolean printUpdate = updatePrintingUsersWithNewMessages(updates.from_id, objArr);
                if (printUpdate) {
                    updatePrintingStrings();
                }
                Utilities.RunOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (printUpdate) {
                            NotificationCenter.Instance.postNotificationName(userPrintUpdate, (long)(updates.from_id));
                            NotificationCenter.Instance.postNotificationName(userPrintUpdateAll);
                        }
                        if (obj.messageOwner.from_id != UserConfig.clientUserId) {
                            long dialog_id;
                            if (obj.messageOwner.to_id.chat_id != 0) {
                                dialog_id = -obj.messageOwner.to_id.chat_id;
                            } else {
                                dialog_id = obj.messageOwner.to_id.user_id;
                            }
                            if (dialog_id != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) {
                                showInAppNotification(obj);
                            }
                        }
                        updateInterfaceWithMessages(updates.from_id, objArr);
                        NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                    }
                });
            } else {
                FileLog.e("tmessages", "need get diff TL_updateShortMessage, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                needGetDiff = true;
            }
        } else if (updates instanceof TLRPC.TL_updatesCombined) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq_start || updates.seq_start == 0 || MessagesStorage.lastSeqValue == updates.seq_start) {
                MessagesStorage.Instance.putUsersAndChats(updates.users, updates.chats, true, true);
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
                needGetDiff = true;
            }
        } else if (updates instanceof TLRPC.TL_updates) {
            if (MessagesStorage.lastSeqValue + 1 == updates.seq || updates.seq == 0 || updates.seq == MessagesStorage.lastSeqValue) {
                MessagesStorage.Instance.putUsersAndChats(updates.users, updates.chats, true, true);
                int lastPtsValue = MessagesStorage.lastPtsValue;
                int lastQtsValue = MessagesStorage.lastQtsValue;
                if (!processUpdateArray(updates.updates, updates.users, updates.chats)) {
                    needGetDiff = true;
                    MessagesStorage.lastPtsValue = lastPtsValue;
                    MessagesStorage.lastQtsValue = lastQtsValue;
                    FileLog.e("tmessages", "need get diff inner TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                } else {
                    MessagesStorage.lastDateValue = updates.date;
                    MessagesStorage.lastSeqValue = updates.seq;
                    if (MessagesStorage.lastQtsValue != lastQtsValue) {
                        needReceivedQueue = true;
                    }
                }
            } else {
                FileLog.e("tmessages", "need get diff TL_updates, seq: " + MessagesStorage.lastSeqValue + " " + updates.seq);
                needGetDiff = true;
            }
        } else if (updates instanceof TLRPC.TL_updatesTooLong) {
            FileLog.e("tmessages", "need get diff TL_updatesTooLong");
            needGetDiff = true;
        }
        if (needGetDiff) {
            getDifference();
        }
        if (needReceivedQueue) {
            TLRPC.TL_messages_receivedQueue req = new TLRPC.TL_messages_receivedQueue();
            req.max_qts = MessagesStorage.lastQtsValue;
            ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            }, null, true, RPCRequest.RPCRequestClassGeneric);
        }
        MessagesStorage.Instance.saveDiffParams(MessagesStorage.lastSeqValue, MessagesStorage.lastPtsValue, MessagesStorage.lastDateValue, MessagesStorage.lastQtsValue);
    }

    public boolean processUpdateArray(ArrayList<TLRPC.Update> updates, final ArrayList<TLRPC.User> usersArr, final ArrayList<TLRPC.Chat> chatsArr) {
        if (updates.isEmpty()) {
            return true;
        }
        long currentTime = System.currentTimeMillis();

        final HashMap<Long, ArrayList<MessageObject>> messages = new HashMap<Long, ArrayList<MessageObject>>(); //to chats+
        final ArrayList<TLRPC.Message> messagesArr = new ArrayList<TLRPC.Message>(); //to db+
        final ArrayList<Integer> markAsReadMessages = new ArrayList<Integer>(); //to db+ and chats+ and dialogs+
        final HashMap<Integer, Integer> markAsReadEncrypted = new HashMap<Integer, Integer>(); //to db+ and chats+ and dialogs+
        final ArrayList<Integer> deletedMessages = new ArrayList<Integer>(); //to db+ and chats+ and dialogs+
        final ArrayList<Long> printChanges = new ArrayList<Long>(); //to chats+ and dialogs
        final ArrayList<TLRPC.ChatParticipants> chatInfoToUpdate = new ArrayList<TLRPC.ChatParticipants>(); //to db+ and chats
        final ArrayList<TLRPC.Update> updatesOnMainThread = new ArrayList<TLRPC.Update>(); //to db+ and every interface+
        final ArrayList<TLRPC.TL_updateEncryptedMessagesRead> tasks = new ArrayList<TLRPC.TL_updateEncryptedMessagesRead>();
        MessageObject lastMessage = null;
        boolean usersAdded = false;

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

        boolean chatAvatarUpdated = false;
        boolean reloadContacts = false;

        for (TLRPC.Update update : updates) {
            if (update instanceof TLRPC.TL_updateNewMessage) {
                TLRPC.TL_updateNewMessage upd = (TLRPC.TL_updateNewMessage)update;
                if (checkForUsers) {
                    if (usersDict.get(upd.message.from_id) == null && users.get(upd.message.from_id) == null || upd.message.to_id.chat_id != 0 && chatsDict.get(upd.message.to_id.chat_id) == null && chats.get(upd.message.to_id.chat_id) == null) {
                        return false;
                    }
                }
                messagesArr.add(upd.message);
                MessageObject obj = new MessageObject(upd.message, usersDict);
                if (obj.type == 11) {
                    chatAvatarUpdated = true;
                }
                long uid;
                if (upd.message.to_id.chat_id != 0) {
                    uid = -upd.message.to_id.chat_id;
                } else {
                    if (upd.message.to_id.user_id == UserConfig.clientUserId) {
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
                if (upd.message.from_id != UserConfig.clientUserId && upd.message.to_id != null) {
                    if (uid != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) {
                        lastMessage = obj;
                    }
                }
            } else if (update instanceof TLRPC.TL_updateMessageID) {
                //can't be here
            } else if (update instanceof TLRPC.TL_updateReadMessages) {
                markAsReadMessages.addAll(update.messages);
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateDeleteMessages) {
                deletedMessages.addAll(update.messages);
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateRestoreMessages) {
                MessagesStorage.lastPtsValue = update.pts;
            } else if (update instanceof TLRPC.TL_updateUserTyping || update instanceof TLRPC.TL_updateChatUserTyping) {
                if (update.user_id != UserConfig.clientUserId) {
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
                        if (!printChanges.contains(uid)) {
                            printChanges.add(uid);
                        }
                    }
                }
            } else if (update instanceof TLRPC.TL_updateChatParticipants) {
                chatInfoToUpdate.add(update.participants);
            } else if (update instanceof TLRPC.TL_updateUserStatus) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateUserName) {
                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateUserPhoto) {
               /*if (!(update.photo instanceof TLRPC.TL_userProfilePhotoEmpty)) { DEPRECATED
                    if (usersDict.containsKey(update.user_id)) {
                        TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                        newMessage.action = new TLRPC.TL_messageActionUserUpdatedPhoto();
                        newMessage.action.newUserPhoto = update.photo;
                        newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                        UserConfig.saveConfig(false);
                        newMessage.unread = true;
                        newMessage.date = update.date;
                        newMessage.from_id = update.user_id;
                        newMessage.to_id = new TLRPC.TL_peerUser();
                        newMessage.to_id.user_id = UserConfig.clientUserId;
                        newMessage.out = false;
                        newMessage.dialog_id = update.user_id;

                        messagesArr.add(newMessage);
                        MessageObject obj = new MessageObject(newMessage, usersDict);
                        ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                        if (arr == null) {
                            arr = new ArrayList<MessageObject>();
                            messages.put(newMessage.dialog_id, arr);
                        }
                        arr.add(obj);
                        if (newMessage.from_id != UserConfig.clientUserId && newMessage.to_id != null) {
                            if (newMessage.dialog_id != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) {
                                lastMessage = obj;
                            }
                        }
                    }
                }*/

                updatesOnMainThread.add(update);
            } else if (update instanceof TLRPC.TL_updateContactRegistered) {
                if (usersDict.containsKey(update.user_id)) {
                    TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                    newMessage.action = new TLRPC.TL_messageActionUserJoined();
                    newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                    UserConfig.saveConfig(false);
                    newMessage.unread = true;
                    newMessage.date = update.date;
                    newMessage.from_id = update.user_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.clientUserId;
                    newMessage.out = false;
                    newMessage.dialog_id = update.user_id;

                    messagesArr.add(newMessage);
                    MessageObject obj = new MessageObject(newMessage, usersDict);
                    ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                    if (arr == null) {
                        arr = new ArrayList<MessageObject>();
                        messages.put(newMessage.dialog_id, arr);
                    }
                    arr.add(obj);
                    if (newMessage.from_id != UserConfig.clientUserId && newMessage.to_id != null) {
                        if (newMessage.dialog_id != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) {
                            lastMessage = obj;
                        }
                    }
                }

                reloadContacts = true;
                //updateContactRegistered#2575bbb9 user_id:int date:int = Update;
                //updateContactRegistered	Импортированный ранее контакт зарегистрировался

            } else if (update instanceof TLRPC.TL_updateContactLink) {
                reloadContacts = true;
                //updateContactLink#51a48a9a user_id:int my_link:contacts.MyLink foreign_link:contacts.ForeignLink = Update;
                //updateContactLink	Изменилась связь с пользователем

            } else if (update instanceof TLRPC.TL_updateActivation) {
                //updateActivation#6f690963 user_id:int = Update;
                //updateActivation	Пользователь активировал свой аккаунт

            } else if (update instanceof TLRPC.TL_updateNewAuthorization) {
                TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                newMessage.action = new TLRPC.TL_messageActionLoginUnknownLocation();
                newMessage.action.title = update.device;
                newMessage.action.address = update.location;
                newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                UserConfig.saveConfig(false);
                newMessage.unread = true;
                newMessage.date = update.date;
                newMessage.from_id = 333000;
                newMessage.to_id = new TLRPC.TL_peerUser();
                newMessage.to_id.user_id = UserConfig.clientUserId;
                newMessage.out = false;
                newMessage.dialog_id = 333000;

                messagesArr.add(newMessage);
                MessageObject obj = new MessageObject(newMessage, usersDict);
                ArrayList<MessageObject> arr = messages.get(newMessage.dialog_id);
                if (arr == null) {
                    arr = new ArrayList<MessageObject>();
                    messages.put(newMessage.dialog_id, arr);
                }
                arr.add(obj);
                if (newMessage.from_id != UserConfig.clientUserId && newMessage.to_id != null) {
                    if (newMessage.dialog_id != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) {
                        lastMessage = obj;
                    }
                }
            } else if (update instanceof TLRPC.TL_updateNewGeoChatMessage) {
                //TODO
            } else if (update instanceof TLRPC.TL_updateNewEncryptedMessage) {
                MessagesStorage.lastQtsValue = update.qts;
                TLRPC.Message message = decryptMessage(((TLRPC.TL_updateNewEncryptedMessage)update).message);
                if (message != null) {
                    int cid = ((TLRPC.TL_updateNewEncryptedMessage)update).message.chat_id;
                    messagesArr.add(message);
                    MessageObject obj = new MessageObject(message, usersDict);
                    long uid = ((long)cid) << 32;
                    ArrayList<MessageObject> arr = messages.get(uid);
                    if (arr == null) {
                        arr = new ArrayList<MessageObject>();
                        messages.put(uid, arr);
                    }
                    arr.add(obj);
                    if (message.from_id != UserConfig.clientUserId && message.to_id != null) {
                        if (uid != openned_dialog_id || ApplicationLoader.lastPauseTime != 0) {
                            lastMessage = obj;
                        }
                    }
                }
            } else if (update instanceof TLRPC.TL_updateEncryptedChatTyping) {
                long uid = ((long)update.chat_id) << 32;
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
                    if (!printChanges.contains(uid)) {
                        printChanges.add(uid);
                    }
                }
            } else if (update instanceof TLRPC.TL_updateEncryptedMessagesRead) {
                markAsReadEncrypted.put(update.chat_id, Math.max(update.max_date, update.date));
                tasks.add((TLRPC.TL_updateEncryptedMessagesRead)update);
            } else if (update instanceof TLRPC.TL_updateEncryption) {
                final TLRPC.EncryptedChat newChat = update.chat;
                long dialog_id = ((long)newChat.id) << 32;
                TLRPC.EncryptedChat existingChat = encryptedChats.get(newChat.id);
                if (existingChat == null) {
                    Semaphore semaphore = new Semaphore(0);
                    ArrayList<TLObject> result = new ArrayList<TLObject>();
                    MessagesStorage.Instance.getEncryptedChat(newChat.id, semaphore, result);
                    try {
                        semaphore.acquire();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    if (result.size() == 2) {
                        existingChat = (TLRPC.EncryptedChat)result.get(0);
                        TLRPC.User user = (TLRPC.User)result.get(1);
                        users.putIfAbsent(user.id, user);
                    }
                }

                if (newChat instanceof TLRPC.TL_encryptedChatRequested && existingChat == null) {
                    int user_id = newChat.participant_id;
                    if (user_id == UserConfig.clientUserId) {
                        user_id = newChat.admin_id;
                    }
                    TLRPC.User user = users.get(user_id);
                    if (user == null) {
                        user = usersDict.get(user_id);
                    }
                    newChat.user_id = user_id;
                    final TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
                    dialog.id = dialog_id;
                    dialog.unread_count = 0;
                    dialog.top_message = 0;
                    dialog.last_message_date = update.date;
                    usersAdded = true;
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (usersArr != null) {
                                for (TLRPC.User user : usersArr) {
                                    users.put(user.id, user);
                                    if (user.id == UserConfig.clientUserId) {
                                        UserConfig.currentUser = user;
                                    }
                                }
                            }
                            if (chatsArr != null) {
                                for (TLRPC.Chat chat : chatsArr) {
                                    chats.put(chat.id, chat);
                                }
                            }


                            dialogs_dict.put(dialog.id, dialog);
                            dialogs.add(dialog);
                            dialogsServerOnly.clear();
                            encryptedChats.put(newChat.id, newChat);
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
                                if ((int)d.id != 0) {
                                    dialogsServerOnly.add(d);
                                }
                            }
                            NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                        }
                    });
                    MessagesStorage.Instance.putEncryptedChat(newChat, user, dialog);
                    acceptSecretChat(newChat);
                } else if (newChat instanceof TLRPC.TL_encryptedChat) {
                    if (existingChat != null && existingChat instanceof TLRPC.TL_encryptedChatWaiting && (existingChat.auth_key == null || existingChat.auth_key.length == 1)) {
                        newChat.a_or_b = existingChat.a_or_b;
                        newChat.user_id = existingChat.user_id;
                        processAcceptedSecretChat(newChat);
                    }
                } else {
                    final TLRPC.EncryptedChat exist = existingChat;
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (exist != null) {
                                newChat.user_id = exist.user_id;
                                newChat.auth_key = exist.auth_key;
                                encryptedChats.put(newChat.id, newChat);
                            }
                            MessagesStorage.Instance.updateEncryptedChat(newChat);
                            NotificationCenter.Instance.postNotificationName(encryptedChatUpdated, newChat);
                        }
                    });
                }
            }
        }
        if (!messages.isEmpty()) {
            for (HashMap.Entry<Long, ArrayList<MessageObject>> pair : messages.entrySet()) {
                Long key = pair.getKey();
                ArrayList<MessageObject> value = pair.getValue();
                boolean printChanged = updatePrintingUsersWithNewMessages(key, value);
                if (printChanged && !printChanges.contains(key)) {
                    printChanges.add(key);
                }
            }
        }
        if (reloadContacts) {
            loadContacts(false);
        }

        if (!printChanges.isEmpty()) {
            updatePrintingStrings();
        }

        final MessageObject lastMessageArg = lastMessage;
        final boolean reloadInterfaceForce = chatAvatarUpdated;

        if (!messagesArr.isEmpty()) {
            MessagesStorage.Instance.putMessages(messagesArr, true, true);
        }

        final boolean usersAddedConst = usersAdded;
        if (!messages.isEmpty() || !markAsReadMessages.isEmpty() || !deletedMessages.isEmpty() || !printChanges.isEmpty() || !chatInfoToUpdate.isEmpty() || !updatesOnMainThread.isEmpty() || !markAsReadEncrypted.isEmpty()) {
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (!usersAddedConst) {
                        if (usersArr != null) {
                            for (TLRPC.User user : usersArr) {
                                users.put(user.id, user);
                                if (user.id == UserConfig.clientUserId) {
                                    UserConfig.currentUser = user;
                                }
                            }
                        }
                        if (chatsArr != null) {
                            for (TLRPC.Chat chat : chatsArr) {
                                chats.put(chat.id, chat);
                            }
                        }
                    }

                    boolean avatarsUpdate = false;
                    if (!updatesOnMainThread.isEmpty()) {
                        ArrayList<TLRPC.User> dbUsers = new ArrayList<TLRPC.User>();
                        ArrayList<TLRPC.User> dbUsersStatus = new ArrayList<TLRPC.User>();
                        for (TLRPC.Update update : updatesOnMainThread) {
                            TLRPC.User toDbUser = new TLRPC.User();
                            toDbUser.id = update.user_id;
                            TLRPC.User currentUser = users.get(update.user_id);
                            if (update instanceof TLRPC.TL_updateUserStatus) {
                                if (!(update.status instanceof TLRPC.TL_userStatusEmpty)) {
                                    if (currentUser != null) {
                                        currentUser.id = update.user_id;
                                        currentUser.status = update.status;
                                        if (update.status instanceof TLRPC.TL_userStatusOnline) {
                                            currentUser.status.was_online = update.status.expires;
                                        } else if (update.status instanceof TLRPC.TL_userStatusOffline) {
                                            currentUser.status.expires = update.status.was_online;
                                        } else {
                                            currentUser.status.was_online = 0;
                                            currentUser.status.expires = 0;
                                        }
                                    }
                                    toDbUser.status = update.status;
                                    dbUsersStatus.add(toDbUser);
                                }
                            } else if (update instanceof TLRPC.TL_updateUserName) {
                                if (currentUser != null) {
                                    currentUser.first_name = update.first_name;
                                    currentUser.last_name = update.last_name;
                                }
                                toDbUser.first_name = update.first_name;
                                toDbUser.last_name = update.last_name;
                                dbUsers.add(toDbUser);
                            } else if (update instanceof TLRPC.TL_updateUserPhoto) {
                                if (currentUser != null) {
                                    currentUser.photo = update.photo;
                                }
                                avatarsUpdate = true;
                                toDbUser.photo = update.photo;
                                dbUsers.add(toDbUser);
                            }
                        }
                        MessagesStorage.Instance.updateUsers(dbUsersStatus, true, true, true);
                        MessagesStorage.Instance.updateUsers(dbUsers, false, true, true);
                    }

                    if (!messages.isEmpty()) {
                        for (HashMap.Entry<Long, ArrayList<MessageObject>> entry : messages.entrySet()) {
                            Long key = entry.getKey();
                            ArrayList<MessageObject> value = entry.getValue();
                            updateInterfaceWithMessages(key, value);
                        }
                    }
                    if (!markAsReadMessages.isEmpty()) {
                        for (Integer id : markAsReadMessages) {
                            MessageObject obj = dialogMessage.get(id);
                            if (obj != null) {
                                obj.messageOwner.unread = false;
                            }
                        }
                        NotificationCenter.Instance.postNotificationName(messagesReaded, markAsReadMessages);
                    }
                    if (!markAsReadEncrypted.isEmpty()) {
                        for (HashMap.Entry<Integer, Integer> entry : markAsReadEncrypted.entrySet()) {
                            NotificationCenter.Instance.postNotificationName(messagesReadedEncrypted, entry.getKey(), entry.getValue());
                            long dialog_id = (long)(entry.getKey()) << 32;
                            TLRPC.TL_dialog dialog = dialogs_dict.get(dialog_id);
                            if (dialog != null) {
                                MessageObject message = dialogMessage.get(dialog.top_message);
                                if (message != null && message.messageOwner.date <= entry.getValue()) {
                                    message.messageOwner.unread = false;
                                }
                            }
                        }
                    }
                    if (!deletedMessages.isEmpty()) {
                        NotificationCenter.Instance.postNotificationName(messagesDeleted, deletedMessages);
                        for (Integer id : deletedMessages) {
                            MessageObject obj = dialogMessage.get(id);
                            if (obj != null) {
                                obj.deleted = true;
                            }
                        }
                    }
                    if (!printChanges.isEmpty()) {
                        for (Long uid : printChanges) {
                            NotificationCenter.Instance.postNotificationName(userPrintUpdate, uid);
                        }
                        NotificationCenter.Instance.postNotificationName(userPrintUpdateAll);
                    }
                    if (!chatInfoToUpdate.isEmpty()) {
                        for (TLRPC.ChatParticipants info : chatInfoToUpdate) {
                            MessagesStorage.Instance.updateChatInfo(info.chat_id, info, true);
                            NotificationCenter.Instance.postNotificationName(chatInfoDidLoaded, info.chat_id, info);
                        }
                    }
                    if (avatarsUpdate || reloadInterfaceForce) {
                        NotificationCenter.Instance.postNotificationName(updateInterfaces, UPDATE_MASK_ALL);
                    } else {
                        NotificationCenter.Instance.postNotificationName(updateInterfaces, UPDATE_MASK_NAME | UPDATE_MASK_STATUS);
                    }
                    if (lastMessageArg != null) {
                        showInAppNotification(lastMessageArg);
                    }
                }
            });
        }

        if (!markAsReadMessages.isEmpty() || !markAsReadEncrypted.isEmpty()) {
            MessagesStorage.Instance.markMessagesAsRead(markAsReadMessages, markAsReadEncrypted, true);
        }
        if (!deletedMessages.isEmpty()) {
            MessagesStorage.Instance.markMessagesAsDeleted(deletedMessages, true);
        }
        if (!deletedMessages.isEmpty()) {
            MessagesStorage.Instance.updateDialogsWithDeletedMessages(deletedMessages, true);
        }
        if (!markAsReadMessages.isEmpty()) {
            MessagesStorage.Instance.updateDialogsWithReadedMessages(markAsReadMessages, true);
        }
        if (!tasks.isEmpty()) {
            for (TLRPC.TL_updateEncryptedMessagesRead update : tasks) {
                MessagesStorage.Instance.createTaskForDate(update.chat_id, update.max_date, update.date, 1);
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

    private void playNotificationSound() {
        if (lastSoundPlay > System.currentTimeMillis() - 1800) {
            return;
        }
        try {
            lastSoundPlay = System.currentTimeMillis();
            soundPool.play(sound, 1, 1, 1, 0, 1);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private void showInAppNotification(MessageObject messageObject) {
        if (ApplicationLoader.lastPauseTime != 0) {
            ApplicationLoader.lastPauseTime = System.currentTimeMillis();
            FileLog.e("tmessages", "reset sleep timeout by recieved message");
        }
        if (messageObject == null) {
            return;
        }
        SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Context.MODE_PRIVATE);
        boolean globalEnabled = preferences.getBoolean("EnableAll", true);
        if (!globalEnabled) {
            return;
        }

        if (ApplicationLoader.lastPauseTime == 0) {
            boolean inAppSounds = preferences.getBoolean("EnableInAppSounds", true);
            boolean inAppVibrate = preferences.getBoolean("EnableInAppVibrate", true);
            boolean inAppPreview = preferences.getBoolean("EnableInAppPreview", true);

            if (inAppSounds || inAppVibrate || inAppPreview) {
                long dialog_id = messageObject.messageOwner.dialog_id;
                int user_id = messageObject.messageOwner.from_id;
                int chat_id = 0;
                if (dialog_id == 0) {
                    if (messageObject.messageOwner.to_id.chat_id != 0) {
                        dialog_id = -messageObject.messageOwner.to_id.chat_id;
                        chat_id = messageObject.messageOwner.to_id.chat_id;
                    } else if (messageObject.messageOwner.to_id.user_id != 0) {
                        if (messageObject.messageOwner.to_id.user_id == UserConfig.clientUserId) {
                            dialog_id = messageObject.messageOwner.from_id;
                        } else {
                            dialog_id = messageObject.messageOwner.to_id.user_id;
                        }
                    }
                } else {
                    TLRPC.EncryptedChat chat = encryptedChats.get((int)(dialog_id >> 32));
                    if (chat == null) {
                        return;
                    }
                }
                if (dialog_id == 0) {
                    return;
                }
                TLRPC.User user = users.get(user_id);
                if (user == null) {
                    return;
                }
                TLRPC.Chat chat;
                if (chat_id != 0) {
                    chat = chats.get(chat_id);
                    if (chat == null) {
                        return;
                    }
                }
                String key = "notify_" + dialog_id;
                boolean value = preferences.getBoolean(key, true);
                if (!value) {
                    return;
                }

                if (inAppPreview) {
                    NotificationCenter.Instance.postNotificationName(701, messageObject);
                }
                if (inAppVibrate) {
                    Vibrator v = (Vibrator)ApplicationLoader.applicationContext.getSystemService(Context.VIBRATOR_SERVICE);
                    v.vibrate(100);
                }
                if (inAppSounds) {
                    playNotificationSound();
                }
            }
        } else {
            long dialog_id = messageObject.messageOwner.dialog_id;
            int chat_id = messageObject.messageOwner.to_id.chat_id;
            int user_id = messageObject.messageOwner.to_id.user_id;
            if (user_id != 0 && user_id == UserConfig.clientUserId) {
                user_id = messageObject.messageOwner.from_id;
            }
            if (dialog_id == 0) {
                if (chat_id != 0) {
                    dialog_id = -chat_id;
                } else if (user_id != 0) {
                    dialog_id = user_id;
                }
            }

            if (dialog_id != 0) {
                String key = "notify_" + dialog_id;
                boolean value = preferences.getBoolean(key, true);
                if (!value) {
                    return;
                }
            }

            boolean groupEnabled = preferences.getBoolean("EnableGroup", true);
            if (chat_id != 0 && !globalEnabled) {
                return;
            }

            boolean globalVibrate = preferences.getBoolean("EnableVibrateAll", true);
            boolean groupVibrate = preferences.getBoolean("EnableVibrateGroup", true);

            String defaultPath = null;
            Uri defaultUri = Settings.System.DEFAULT_NOTIFICATION_URI;
            if (defaultUri != null) {
                defaultPath = defaultUri.getPath();
            }

            String globalSound = preferences.getString("GlobalSoundPath", defaultPath);
            String chatSound = preferences.getString("GroupSoundPath", defaultPath);
            String userSoundPath = null;
            String chatSoundPath = null;

            NotificationManager mNotificationManager = (NotificationManager)ApplicationLoader.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE);
            Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
            String msg = null;

            if ((int)dialog_id != 0) {
                if (chat_id != 0) {
                    intent.putExtra("chatId", chat_id);
                }
                if (user_id != 0) {
                    intent.putExtra("userId", user_id);
                }

                if (chat_id == 0 && user_id != 0) {
                    TLRPC.User u = users.get(user_id);
                    if (u == null) {
                        return;
                    }
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserJoined) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationContactJoined, Utilities.formatName(u.first_name, u.last_name));
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationContactNewPhoto, Utilities.formatName(u.first_name, u.last_name));
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationUnrecognizedDevice, messageObject.messageOwner.action.title, messageObject.messageOwner.action.address);
                        }
                    } else {
                        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) {
                            if (messageObject.messageOwner.message != null && messageObject.messageOwner.message.length() != 0) {
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageText, Utilities.formatName(u.first_name, u.last_name), messageObject.messageOwner.message);
                            } else {
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageNoText, Utilities.formatName(u.first_name, u.last_name));
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessagePhoto, Utilities.formatName(u.first_name, u.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageVideo, Utilities.formatName(u.first_name, u.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageContact, Utilities.formatName(u.first_name, u.last_name));
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageMap, Utilities.formatName(u.first_name, u.last_name));
                        }
                    }
                } else if (chat_id != 0 && user_id == 0) {
                    TLRPC.Chat chat = chats.get(chat_id);
                    if (chat == null) {
                        return;
                    }
                    TLRPC.User u = users.get(messageObject.messageOwner.from_id);
                    if (u == null) {
                        return;
                    }
                    if (messageObject.messageOwner instanceof TLRPC.TL_messageService) {
                        if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatAddUser) {
                            if (messageObject.messageOwner.action.user_id == UserConfig.clientUserId) {
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationInvitedToGroup, Utilities.formatName(u.first_name, u.last_name), chat.title);
                            } else {
                                TLRPC.User u2 = users.get(messageObject.messageOwner.action.user_id);
                                if (u2 == null) {
                                    return;
                                }
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationGroupAddMember, Utilities.formatName(u.first_name, u.last_name), chat.title, Utilities.formatName(u2.first_name, u2.last_name));
                            }
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationEditedGroupName, Utilities.formatName(u.first_name, u.last_name), messageObject.messageOwner.action.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatEditPhoto || messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationEditedGroupPhoto, Utilities.formatName(u.first_name, u.last_name), chat.title);
                        } else if (messageObject.messageOwner.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                            if (messageObject.messageOwner.action.user_id == UserConfig.clientUserId) {
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationGroupKickYou, Utilities.formatName(u.first_name, u.last_name), chat.title);
                            } else if (messageObject.messageOwner.action.user_id == u.id) {
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationGroupLeftMember, Utilities.formatName(u.first_name, u.last_name), chat.title);
                            } else {
                                TLRPC.User u2 = users.get(messageObject.messageOwner.action.user_id);
                                if (u2 == null) {
                                    return;
                                }
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationGroupKickMember, Utilities.formatName(u.first_name, u.last_name), chat.title, Utilities.formatName(u2.first_name, u2.last_name));
                            }
                        }
                    } else {
                        if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaEmpty) {
                            if (messageObject.messageOwner.message != null && messageObject.messageOwner.message.length() != 0) {
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageGroupText, Utilities.formatName(u.first_name, u.last_name), chat.title, messageObject.messageOwner.message);
                            } else {
                                msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageGroupNoText, Utilities.formatName(u.first_name, u.last_name), chat.title);
                            }
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageGroupPhoto, Utilities.formatName(u.first_name, u.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageGroupVideo, Utilities.formatName(u.first_name, u.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaContact) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageGroupContact, Utilities.formatName(u.first_name, u.last_name), chat.title);
                        } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaGeo) {
                            msg = ApplicationLoader.applicationContext.getString(R.string.NotificationMessageGroupMap, Utilities.formatName(u.first_name, u.last_name), chat.title);
                        }
                    }
                }
            } else {
                msg = ApplicationLoader.applicationContext.getString(R.string.YouHaveNewMessage);
            }
            if (msg == null) {
                return;
            }

            boolean needVibrate = false;

            if (user_id != 0) {
                userSoundPath = preferences.getString("sound_path_" + user_id, null);
                needVibrate = globalVibrate;
            }
            if (chat_id != 0) {
                chatSoundPath = preferences.getString("sound_chat_path_" + chat_id, null);
                needVibrate = groupVibrate;
            }

            String choosenSoundPath = null;

            if (user_id != 0) {
                if (userSoundPath != null) {
                    choosenSoundPath = userSoundPath;
                } else if (globalSound != null) {
                    choosenSoundPath = globalSound;
                }
            } else if (chat_id != 0) {
                if (chatSoundPath != null) {
                    choosenSoundPath = chatSoundPath;
                } else if (chatSound != null) {
                    choosenSoundPath = chatSound;
                }
            } else {
                choosenSoundPath = globalSound;
            }

            intent.setAction("com.tmessages.openchat" + Math.random() + Integer.MAX_VALUE);
            intent.setFlags(32768);
            PendingIntent contentIntent = PendingIntent.getActivity(ApplicationLoader.applicationContext, 0, intent, PendingIntent.FLAG_ONE_SHOT);

            NotificationCompat.Builder mBuilder = new NotificationCompat.Builder(ApplicationLoader.applicationContext)
                    .setContentTitle(ApplicationLoader.applicationContext.getString(R.string.AppName))
                    .setSmallIcon(R.drawable.notification)
                    .setStyle(new NotificationCompat.BigTextStyle()
                            .bigText(msg))
                    .setContentText(msg)
                    .setAutoCancel(true)
                    .setTicker(msg);

            if (needVibrate) {
                mBuilder.setVibrate(new long[]{0, 100, 0, 100});
            }
            if (choosenSoundPath != null && !choosenSoundPath.equals("NoSound")) {
                if (choosenSoundPath.equals(defaultPath)) {
                    mBuilder.setSound(defaultUri);
                } else {
                    mBuilder.setSound(Uri.parse(choosenSoundPath));
                }
            }

            mBuilder.setContentIntent(contentIntent);
            mNotificationManager.cancel(1);
            Notification notification = mBuilder.build();
            notification.ledARGB = 0xff00ff00;
            notification.ledOnMS = 1000;
            notification.ledOffMS = 1000;
            notification.flags |= Notification.FLAG_SHOW_LIGHTS;
            try {
                mNotificationManager.notify(1, notification);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
        }
    }

    private void updateInterfaceWithMessages(long uid, ArrayList<MessageObject> messages) {
        int unreadCount = 0;
        MessageObject lastMessage = null;
        int lastDate = 0;
        TLRPC.TL_dialog dialog = dialogs_dict.get(uid);

        NotificationCenter.Instance.postNotificationName(didReceivedNewMessages, uid, messages);

        for (MessageObject message : messages) {
            if (message.messageOwner.unread && message.messageOwner.from_id != UserConfig.clientUserId) {
                unreadCount++;
            }
            if (lastMessage == null || message.messageOwner.date > lastDate) {
                lastMessage = message;
                lastDate = message.messageOwner.date;
            }
        }

        if (dialog == null) {
            dialog = new TLRPC.TL_dialog();
            dialog.id = uid;
            dialog.unread_count = unreadCount;
            dialog.top_message = lastMessage.messageOwner.id;
            dialog.last_message_date = lastMessage.messageOwner.date;
            dialogs_dict.put(uid, dialog);
            dialogs.add(dialog);
            dialogMessage.put(lastMessage.messageOwner.id, lastMessage);
        } else {
            dialogMessage.remove(dialog.top_message);
            dialog.unread_count += unreadCount;
            dialog.top_message = lastMessage.messageOwner.id;
            dialog.last_message_date = lastMessage.messageOwner.date;
            dialogMessage.put(lastMessage.messageOwner.id, lastMessage);
        }

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
            if ((int)d.id != 0) {
                dialogsServerOnly.add(d);
            }
        }
    }

    public TLRPC.Message decryptMessage(TLRPC.EncryptedMessage message) {
        TLRPC.EncryptedChat chat = encryptedChats.get(message.chat_id);
        if (chat == null) {
            Semaphore semaphore = new Semaphore(0);
            ArrayList<TLObject> result = new ArrayList<TLObject>();
            MessagesStorage.Instance.getEncryptedChat(message.chat_id, semaphore, result);
            try {
                semaphore.acquire();
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }
            if (result.size() == 2) {
                chat = (TLRPC.EncryptedChat)result.get(0);
                TLRPC.User user = (TLRPC.User)result.get(1);
                encryptedChats.put(chat.id, chat);
                users.putIfAbsent(user.id, user);
            }
        }
        if (chat == null) {
            return null;
        }
        SerializedData is = new SerializedData(message.bytes);
        long fingerprint = is.readInt64();
        if (chat.key_fingerprint == fingerprint) {
            byte[] messageKey = is.readData(16);
            MessageKeyData keyData = Utilities.generateMessageKeyData(chat.auth_key, messageKey, false);

            byte[] messageData = is.readData(message.bytes.length - 24);
            messageData = Utilities.aesIgeEncryption(messageData, keyData.aesKey, keyData.aesIv, false, false);

            is = new SerializedData(messageData);
            int len = is.readInt32();
            TLObject object = TLClassStore.Instance().TLdeserialize(is, is.readInt32());
            if (object != null) {

                int from_id = chat.admin_id;
                if (from_id == UserConfig.clientUserId) {
                    from_id = chat.participant_id;
                }

                if (object instanceof TLRPC.TL_decryptedMessage) {
                    TLRPC.TL_decryptedMessage decryptedMessage = (TLRPC.TL_decryptedMessage)object;
                    TLRPC.TL_message newMessage = new TLRPC.TL_message();
                    newMessage.message = decryptedMessage.message;
                    newMessage.date = message.date;
                    newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                    UserConfig.saveConfig(false);
                    newMessage.from_id = from_id;
                    newMessage.to_id = new TLRPC.TL_peerUser();
                    newMessage.to_id.user_id = UserConfig.clientUserId;
                    newMessage.out = false;
                    newMessage.unread = true;
                    newMessage.dialog_id = ((long)chat.id) << 32;
                    newMessage.ttl = chat.ttl;
                    if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaEmpty) {
                        newMessage.media = new TLRPC.TL_messageMediaEmpty();
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaContact) {
                        newMessage.media = new TLRPC.TL_messageMediaContact();
                        newMessage.media.last_name = decryptedMessage.media.last_name;
                        newMessage.media.first_name = decryptedMessage.media.first_name;
                        newMessage.media.phone_number = decryptedMessage.media.phone_number;
                        newMessage.media.user_id = decryptedMessage.media.user_id;
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaGeoPoint) {
                        newMessage.media = new TLRPC.TL_messageMediaGeo();
                        newMessage.media.geo = new TLRPC.TL_geoPoint();
                        newMessage.media.geo.lat = decryptedMessage.media.lat;
                        newMessage.media.geo._long = decryptedMessage.media._long;
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaPhoto) {
                        if (decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv.length != 32) {
                            return null;
                        }
                        newMessage.media = new TLRPC.TL_messageMediaPhoto();
                        newMessage.media.photo = new TLRPC.TL_photo();
                        newMessage.media.photo.user_id = newMessage.from_id;
                        newMessage.media.photo.date = newMessage.date;
                        newMessage.media.photo.caption = "";
                        newMessage.media.photo.geo = new TLRPC.TL_geoPointEmpty();
                        TLRPC.TL_photoCachedSize small = new TLRPC.TL_photoCachedSize();
                        small.w = decryptedMessage.media.thumb_w;
                        small.h = decryptedMessage.media.thumb_h;
                        small.bytes = decryptedMessage.media.thumb;
                        small.type = "s";
                        small.location = new TLRPC.TL_fileLocationUnavailable();
                        newMessage.media.photo.sizes.add(small);

                        TLRPC.TL_photoSize big = new TLRPC.TL_photoSize();
                        big.w = decryptedMessage.media.w;
                        big.h = decryptedMessage.media.h;
                        big.type = "x";
                        big.size = message.file.size;
                        big.location = new TLRPC.TL_fileEncryptedLocation();
                        big.location.key = decryptedMessage.media.key;
                        big.location.iv = decryptedMessage.media.iv;
                        big.location.dc_id = message.file.dc_id;
                        big.location.volume_id = message.file.id;
                        big.location.secret = message.file.access_hash;
                        big.location.local_id = message.file.key_fingerprint;
                        newMessage.media.photo.sizes.add(big);
                    } else if (decryptedMessage.media instanceof TLRPC.TL_decryptedMessageMediaVideo) {
                        if (decryptedMessage.media.key.length != 32 || decryptedMessage.media.iv.length != 32) {
                            return null;
                        }
                        newMessage.media = new TLRPC.TL_messageMediaVideo();
                        newMessage.media.video = new TLRPC.TL_videoEncrypted();
                        newMessage.media.video.thumb = new TLRPC.TL_photoCachedSize();
                        newMessage.media.video.thumb.bytes = decryptedMessage.media.thumb;
                        newMessage.media.video.thumb.w = decryptedMessage.media.thumb_w;
                        newMessage.media.video.thumb.h = decryptedMessage.media.thumb_h;
                        newMessage.media.video.thumb.type = "s";
                        newMessage.media.video.thumb.location = new TLRPC.TL_fileLocationUnavailable();
                        newMessage.media.video.duration = decryptedMessage.media.duration;
                        newMessage.media.video.dc_id = message.file.dc_id;
                        newMessage.media.video.w = decryptedMessage.media.w;
                        newMessage.media.video.h = decryptedMessage.media.h;
                        newMessage.media.video.date = message.date;
                        newMessage.media.video.caption = "";
                        newMessage.media.video.user_id = from_id;
                        newMessage.media.video.size = message.file.size;
                        newMessage.media.video.id = message.file.id;
                        newMessage.media.video.access_hash = message.file.access_hash;
                        newMessage.media.video.key = decryptedMessage.media.key;
                        newMessage.media.video.iv = decryptedMessage.media.iv;
                    } else {
                        return null;
                    }
                    return newMessage;
                } else if (object instanceof TLRPC.TL_decryptedMessageService) {
                    TLRPC.TL_decryptedMessageService serviceMessage = (TLRPC.TL_decryptedMessageService)object;
                    if (serviceMessage.action instanceof TLRPC.TL_decryptedMessageActionSetMessageTTL) {
                        TLRPC.TL_messageService newMessage = new TLRPC.TL_messageService();
                        newMessage.action = new TLRPC.TL_messageActionTTLChange();
                        newMessage.action.ttl = chat.ttl = serviceMessage.action.ttl_seconds;
                        newMessage.local_id = newMessage.id = UserConfig.getNewMessageId();
                        UserConfig.saveConfig(false);
                        newMessage.unread = true;
                        newMessage.date = message.date;
                        newMessage.from_id = from_id;
                        newMessage.to_id = new TLRPC.TL_peerUser();
                        newMessage.to_id.user_id = UserConfig.clientUserId;
                        newMessage.out = false;
                        newMessage.dialog_id = ((long)chat.id) << 32;
                        MessagesStorage.Instance.updateEncryptedChatTTL(chat);
                        return newMessage;
                    }
                }
            }
        }
        return null;
    }

    public void processAcceptedSecretChat(final TLRPC.EncryptedChat encryptedChat) {
        BigInteger i_authKey = new BigInteger(1, encryptedChat.g_a_or_b);
        i_authKey = i_authKey.modPow(new BigInteger(1, encryptedChat.a_or_b), new BigInteger(1, MessagesStorage.secretPBytes));

        byte[] authKey = i_authKey.toByteArray();
        if (authKey.length > 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
            authKey = correctedAuth;
        } else if (authKey.length < 256) {
            byte[] correctedAuth = new byte[256];
            System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
            for (int a = 0; a < 256 - authKey.length; a++) {
                authKey[a] = 0;
            }
            authKey = correctedAuth;
        }
        byte[] authKeyHash = Utilities.computeSHA1(authKey);
        byte[] authKeyId = new byte[8];
        System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
        long fingerprint = Utilities.bytesToLong(authKeyId);
        if (encryptedChat.key_fingerprint == fingerprint) {
            encryptedChat.auth_key = authKey;
            MessagesStorage.Instance.updateEncryptedChat(encryptedChat);
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    encryptedChats.put(encryptedChat.id, encryptedChat);
                    NotificationCenter.Instance.postNotificationName(encryptedChatUpdated, encryptedChat);
                }
            });
        } else {
            final TLRPC.TL_encryptedChatDiscarded newChat = new TLRPC.TL_encryptedChatDiscarded();
            newChat.id = encryptedChat.id;
            newChat.user_id = encryptedChat.user_id;
            newChat.auth_key = encryptedChat.auth_key;
            MessagesStorage.Instance.updateEncryptedChat(newChat);
            Utilities.RunOnUIThread(new Runnable() {
                @Override
                public void run() {
                    encryptedChats.put(newChat.id, newChat);
                    NotificationCenter.Instance.postNotificationName(encryptedChatUpdated, newChat);
                }
            });
            declineSecretChat(encryptedChat.id);
        }
    }

    public void declineSecretChat(int chat_id) {
        TLRPC.TL_messages_discardEncryption req = new TLRPC.TL_messages_discardEncryption();
        req.chat_id = chat_id;
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void acceptSecretChat(final TLRPC.EncryptedChat encryptedChat) {
        if (acceptingChats.get(encryptedChat.id) != null) {
            return;
        }
        acceptingChats.put(encryptedChat.id, encryptedChat);
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = MessagesStorage.lastSecretVersion;
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig)response;
                    if (response instanceof TLRPC.TL_messages_dhConfig) {
                        if (!Utilities.isGoodPrime(res.p)) {
                            acceptingChats.remove(encryptedChat.id);
                            declineSecretChat(encryptedChat.id);
                            return;
                        }

                        MessagesStorage.secretPBytes = res.p;
                        MessagesStorage.secretG = res.g;
                        MessagesStorage.lastSecretVersion = res.version;
                        MessagesStorage.Instance.saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
                    }
                    byte[] salt = new byte[256];
                    for (int a = 0; a < 256; a++) {
                        salt[a] = (byte)((byte)(random.nextDouble() * 255) ^ res.random[a]);
                    }
                    encryptedChat.a_or_b = salt;
                    BigInteger i_g_b = BigInteger.valueOf(MessagesStorage.secretG);
                    i_g_b = i_g_b.modPow(new BigInteger(1, salt), new BigInteger(1, MessagesStorage.secretPBytes));
                    byte[] g_b = i_g_b.toByteArray();
                    if (g_b.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(g_b, 1, correctedAuth, 0, 256);
                        g_b = correctedAuth;
                    }

                    BigInteger i_authKey = new BigInteger(1, encryptedChat.g_a);
                    i_authKey = i_authKey.modPow(new BigInteger(1, salt), new BigInteger(1, MessagesStorage.secretPBytes));

                    byte[] authKey = i_authKey.toByteArray();
                    if (authKey.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, authKey.length - 256, correctedAuth, 0, 256);
                        authKey = correctedAuth;
                    } else if (authKey.length < 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(authKey, 0, correctedAuth, 256 - authKey.length, authKey.length);
                        for (int a = 0; a < 256 - authKey.length; a++) {
                            authKey[a] = 0;
                        }
                        authKey = correctedAuth;
                    }
                    byte[] authKeyHash = Utilities.computeSHA1(authKey);
                    byte[] authKeyId = new byte[8];
                    System.arraycopy(authKeyHash, authKeyHash.length - 8, authKeyId, 0, 8);
                    encryptedChat.auth_key = authKey;

                    TLRPC.TL_messages_acceptEncryption req2 = new TLRPC.TL_messages_acceptEncryption();
                    req2.g_b = g_b;
                    req2.peer = new TLRPC.TL_inputEncryptedChat();
                    req2.peer.chat_id = encryptedChat.id;
                    req2.peer.access_hash = encryptedChat.access_hash;
                    req2.key_fingerprint = Utilities.bytesToLong(authKeyId);
                    ConnectionsManager.Instance.performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(TLObject response, TLRPC.TL_error error) {
                            acceptingChats.remove(encryptedChat.id);
                            if (error == null) {
                                final TLRPC.EncryptedChat newChat = (TLRPC.EncryptedChat)response;
                                newChat.auth_key = encryptedChat.auth_key;
                                newChat.user_id = encryptedChat.user_id;
                                MessagesStorage.Instance.updateEncryptedChat(newChat);
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        encryptedChats.put(newChat.id, newChat);
                                        NotificationCenter.Instance.postNotificationName(encryptedChatUpdated, newChat);
                                    }
                                });
                            }
                        }
                    }, null, true, RPCRequest.RPCRequestClassGeneric);
                } else {
                    acceptingChats.remove(encryptedChat.id);
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric);
    }

    public void startSecretChat(final Context context, final int user_id) {
        final ProgressDialog progressDialog = new ProgressDialog(context);
        progressDialog.setMessage(context.getString(R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);
        progressDialog.show();
        TLRPC.TL_messages_getDhConfig req = new TLRPC.TL_messages_getDhConfig();
        req.random_length = 256;
        req.version = MessagesStorage.lastSecretVersion;
        ConnectionsManager.Instance.performRpc(req, new RPCRequest.RPCRequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error == null) {
                    TLRPC.messages_DhConfig res = (TLRPC.messages_DhConfig)response;
                    if (response instanceof TLRPC.TL_messages_dhConfig) {
                        if (!Utilities.isGoodPrime(res.p)) {
                            Utilities.RunOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    if (!((ActionBarActivity)context).isFinishing()) {
                                        progressDialog.dismiss();
                                    }
                                }
                            });
                            return;
                        }
                        MessagesStorage.secretPBytes = res.p;
                        MessagesStorage.secretG = res.g;
                        MessagesStorage.lastSecretVersion = res.version;
                        MessagesStorage.Instance.saveSecretParams(MessagesStorage.lastSecretVersion, MessagesStorage.secretG, MessagesStorage.secretPBytes);
                    }
                    final byte[] salt = new byte[256];
                    for (int a = 0; a < 256; a++) {
                        salt[a] = (byte)((byte)(random.nextDouble() * 255) ^ res.random[a]);
                    }
                    BigInteger i_g_a = BigInteger.valueOf(MessagesStorage.secretG);
                    i_g_a = i_g_a.modPow(new BigInteger(1, salt), new BigInteger(1, MessagesStorage.secretPBytes));
                    byte[] g_a = i_g_a.toByteArray();
                    if (g_a.length > 256) {
                        byte[] correctedAuth = new byte[256];
                        System.arraycopy(g_a, 1, correctedAuth, 0, 256);
                        g_a = correctedAuth;
                    }

                    final TLRPC.User user = users.get(user_id);
                    TLRPC.InputUser inputUser;
                    if (user instanceof TLRPC.TL_userForeign || user instanceof TLRPC.TL_userRequest) {
                        inputUser = new TLRPC.TL_inputUserForeign();
                        inputUser.user_id = user.id;
                        inputUser.access_hash = user.access_hash;
                    } else {
                        inputUser = new TLRPC.TL_inputUserContact();
                        inputUser.user_id = user.id;
                    }
                    TLRPC.TL_messages_requestEncryption req2 = new TLRPC.TL_messages_requestEncryption();
                    req2.g_a = g_a;
                    req2.user_id = inputUser;
                    req2.random_id = (int)(random.nextDouble() * Integer.MAX_VALUE);
                    ConnectionsManager.Instance.performRpc(req2, new RPCRequest.RPCRequestDelegate() {
                        @Override
                        public void run(final TLObject response, TLRPC.TL_error error) {
                            if (error == null) {
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!((ActionBarActivity)context).isFinishing()) {
                                            progressDialog.dismiss();
                                        }
                                        TLRPC.EncryptedChat chat = (TLRPC.EncryptedChat)response;
                                        chat.user_id = chat.participant_id;
                                        encryptedChats.put(chat.id, chat);
                                        chat.a_or_b = salt;
                                        TLRPC.TL_dialog dialog = new TLRPC.TL_dialog();
                                        dialog.id = ((long)chat.id) << 32;
                                        dialog.unread_count = 0;
                                        dialog.top_message = 0;
                                        dialog.last_message_date = ConnectionsManager.Instance.getCurrentTime();
                                        dialogs_dict.put(dialog.id, dialog);
                                        dialogs.add(dialog);
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
                                            if ((int)d.id != 0) {
                                                dialogsServerOnly.add(d);
                                            }
                                        }
                                        NotificationCenter.Instance.postNotificationName(dialogsNeedReload);
                                        MessagesStorage.Instance.putEncryptedChat(chat, user, dialog);
                                        NotificationCenter.Instance.postNotificationName(encryptedChatCreated, chat);
                                    }
                                });
                            } else {
                                Utilities.RunOnUIThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (!((ActionBarActivity)context).isFinishing()) {
                                            progressDialog.dismiss();
                                            AlertDialog.Builder builder = new AlertDialog.Builder(context);
                                            builder.setTitle(context.getString(R.string.AppName));
                                            builder.setMessage(String.format(context.getString(R.string.CreateEncryptedChatOutdatedError), user.first_name, user.first_name));
                                            builder.setPositiveButton(ApplicationLoader.applicationContext.getString(R.string.OK), null);
                                            builder.show().setCanceledOnTouchOutside(true);
                                        }
                                    }
                                });
                            }
                        }
                    }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
                } else {
                    Utilities.RunOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (!((ActionBarActivity)context).isFinishing()) {
                                progressDialog.dismiss();
                            }
                        }
                    });
                }
            }
        }, null, true, RPCRequest.RPCRequestClassGeneric | RPCRequest.RPCRequestClassFailOnServerErrors);
    }
}
