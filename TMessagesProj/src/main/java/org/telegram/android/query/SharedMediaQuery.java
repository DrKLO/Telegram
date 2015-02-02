/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.android.query;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.ImageLoader;
import org.telegram.android.MessageObject;
import org.telegram.android.MessagesController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLClassStore;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

public class SharedMediaQuery {

    public final static int MEDIA_PHOTOVIDEO = 0;
    public final static int MEDIA_FILE = 1;
    public final static int MEDIA_AUDIO = 2;

    public static void loadMedia(final long uid, final int offset, final int count, final int max_id, final int type, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            loadMediaDatabase(uid, offset, count, max_id, type, classGuid);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.offset = offset;
            req.limit = count;
            req.max_id = max_id;
            if (type == MEDIA_PHOTOVIDEO) {
                req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            } else if (type == MEDIA_FILE) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (type == MEDIA_AUDIO) {
                req.filter = new TLRPC.TL_inputMessagesFilterAudio();
            }
            req.q = "";
            if (uid < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = MessagesController.getInstance().getUser(lower_part);
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
                        processLoadedMedia(res, uid, offset, count, max_id, type, false, classGuid);
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public static void getMediaCount(final long uid, final int type, final int classGuid, boolean fromCache) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            getMediaCountDatabase(uid, type, classGuid);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.offset = 0;
            req.limit = 1;
            req.max_id = 0;
            if (type == MEDIA_PHOTOVIDEO) {
                req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            } else if (type == MEDIA_FILE) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (type == MEDIA_AUDIO) {
                req.filter = new TLRPC.TL_inputMessagesFilterAudio();
            }
            req.q = "";
            if (uid < 0) {
                req.peer = new TLRPC.TL_inputPeerChat();
                req.peer.chat_id = -lower_part;
            } else {
                TLRPC.User user = MessagesController.getInstance().getUser(lower_part);
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
                                MessagesController.getInstance().putUsers(res.users, false);
                                MessagesController.getInstance().putChats(res.chats, false);
                            }
                        });

                        if (res instanceof TLRPC.TL_messages_messagesSlice) {
                            processLoadedMediaCount(res.count, uid, type, classGuid, false);
                        } else {
                            processLoadedMediaCount(res.messages.size(), uid, type, classGuid, false);
                        }
                    }
                }
            });
            ConnectionsManager.getInstance().bindRequestToGuid(reqId, classGuid);
        }
    }

    public static int getMediaType(TLRPC.Message message) {
        if (message == null) {
            return -1;
        }
        if (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaVideo) {
            return SharedMediaQuery.MEDIA_PHOTOVIDEO;
        } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            if (MessageObject.isStickerMessage(message)) {
                return -1;
            } else {
                return SharedMediaQuery.MEDIA_FILE;
            }
        } else if (message.media instanceof TLRPC.TL_messageMediaAudio) {
            return SharedMediaQuery.MEDIA_AUDIO;
        }
        return -1;
    }

    public static boolean canAddMessageToMedia(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_message_secret && message.media instanceof TLRPC.TL_messageMediaPhoto && message.ttl != 0 && message.ttl <= 60) {
            return false;
        } else if (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaVideo || message.media instanceof TLRPC.TL_messageMediaDocument || message.media instanceof TLRPC.TL_messageMediaAudio) {
            return true;
        }
        return false;
    }

    private static void processLoadedMedia(final TLRPC.messages_Messages res, final long uid, int offset, int count, int max_id, final int type, final boolean fromCache, final int classGuid) {
        int lower_part = (int)uid;
        if (fromCache && res.messages.isEmpty() && lower_part != 0) {
            loadMedia(uid, offset, count, max_id, type, false, classGuid);
        } else {
            if (!fromCache) {
                ImageLoader.saveMessagesThumbs(res.messages);
                MessagesStorage.getInstance().putUsersAndChats(res.users, res.chats, true, true);
                putMediaDatabase(uid, type, res.messages);
            }

            final HashMap<Integer, TLRPC.User> usersLocal = new HashMap<>();
            for (TLRPC.User u : res.users) {
                usersLocal.put(u.id, u);
            }
            final ArrayList<MessageObject> objects = new ArrayList<>();
            for (TLRPC.Message message : res.messages) {
                objects.add(new MessageObject(message, usersLocal, false));
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
                    MessagesController.getInstance().putUsers(res.users, fromCache);
                    MessagesController.getInstance().putChats(res.chats, fromCache);
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.mediaDidLoaded, uid, totalCount, objects, fromCache, classGuid, type);
                }
            });
        }
    }

    private static void processLoadedMediaCount(final int count, final long uid, final int type, final int classGuid, final boolean fromCache) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                int lower_part = (int)uid;
                if (fromCache && count == -1 && lower_part != 0) {
                    getMediaCount(uid, type, classGuid, false);
                } else {
                    if (!fromCache) {
                        putMediaCountDatabase(uid, type, count);
                    }
                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.mediaCountDidLoaded, uid, (fromCache && count == -1 ? 0 : count), fromCache, type);
                }
            }
        });
    }

    private static void putMediaCountDatabase(final long uid, final int type, final int count) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state2 = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?)");
                    state2.requery();
                    state2.bindLong(1, uid);
                    state2.bindInteger(2, type);
                    state2.bindInteger(3, count);
                    state2.step();
                    state2.dispose();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private static void getMediaCountDatabase(final long uid, final int type, final int classGuid) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    int count = -1;
                    SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT count FROM media_counts_v2 WHERE uid = %d AND type = %d LIMIT 1", uid, type));
                    if (cursor.next()) {
                        count = cursor.intValue(0);
                    }
                    cursor.dispose();
                    int lower_part = (int)uid;
                    if (count == -1 && lower_part == 0) {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM media_v2 WHERE uid = %d AND type = %d LIMIT 1", uid, type));
                        if (cursor.next()) {
                            count = cursor.intValue(0);
                        }
                        cursor.dispose();

                        /*cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, send_state, date FROM messages WHERE uid = %d ORDER BY mid ASC LIMIT %d", uid, 1000));
                        ArrayList<TLRPC.Message> photos = new ArrayList<>();
                        ArrayList<TLRPC.Message> docs = new ArrayList<>();
                        while (cursor.next()) {
                            ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(1));
                            if (data != null && cursor.byteBufferValue(1, data.buffer) != 0) {
                                TLRPC.Message message = (TLRPC.Message) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                                MessageObject.setIsUnread(message, cursor.intValue(0) != 1);
                                message.date = cursor.intValue(2);
                                message.send_state = cursor.intValue(1);
                                message.dialog_id = uid;
                                if (message.ttl > 60 && message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaVideo) {
                                    photos.add(message);
                                } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                                    docs.add(message);
                                }
                            }
                            MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                        }
                        cursor.dispose();
                        if (!photos.isEmpty() || !docs.isEmpty()) {
                            MessagesStorage.getInstance().getDatabase().beginTransaction();
                            if (!photos.isEmpty()) {
                                putMediaDatabaseInternal(uid, MEDIA_PHOTOVIDEO, photos);
                            }
                            if (docs.isEmpty()) {
                                putMediaDatabaseInternal(uid, MEDIA_FILE, docs);
                            }
                            MessagesStorage.getInstance().getDatabase().commitTransaction();
                        }*/

                        if (count != -1) {
                            putMediaCountDatabase(uid, type, count);
                        }
                    }
                    processLoadedMediaCount(count, uid, type, classGuid, true);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private static void loadMediaDatabase(final long uid, final int offset, final int count, final int max_id, final int type, final int classGuid) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                TLRPC.TL_messages_messages res = new TLRPC.TL_messages_messages();
                try {
                    ArrayList<Integer> loadedUsers = new ArrayList<>();
                    ArrayList<Integer> fromUser = new ArrayList<>();

                    SQLiteCursor cursor;

                    if ((int)uid != 0) {
                        if (max_id != 0) {
                            cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, max_id, type, count));
                        } else {
                            cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d,%d", uid, type, offset, count));
                        }
                    } else {
                        if (max_id != 0) {
                            cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v2 as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, max_id, type, count));
                        } else {
                            cursor = MessagesStorage.getInstance().getDatabase().queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v2 as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND type = %d ORDER BY m.mid ASC LIMIT %d,%d", uid, type, offset, count));
                        }
                    }

                    while (cursor.next()) {
                        ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                        if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                            TLRPC.Message message = (TLRPC.Message) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
                            message.id = cursor.intValue(1);
                            message.dialog_id = uid;
                            if ((int)uid == 0) {
                                message.random_id = cursor.longValue(2);
                            }
                            res.messages.add(message);
                            fromUser.add(message.from_id);
                        }
                        MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                    }
                    cursor.dispose();

                    StringBuilder usersToLoad = new StringBuilder();
                    for (int uid : fromUser) {
                        if (!loadedUsers.contains(uid)) {
                            if (usersToLoad.length() != 0) {
                                usersToLoad.append(",");
                            }
                            usersToLoad.append(uid);
                            loadedUsers.add(uid);
                        }
                    }
                    if (usersToLoad.length() != 0) {
                        MessagesStorage.getInstance().getUsersInternal(usersToLoad.toString(), res.users);
                    }
                } catch (Exception e) {
                    res.messages.clear();
                    res.chats.clear();
                    res.users.clear();
                    FileLog.e("tmessages", e);
                } finally {
                    processLoadedMedia(res, uid, offset, count, max_id, type, true, classGuid);
                }
            }
        });
    }

    private static void putMediaDatabaseInternal(final long uid, final int type, final ArrayList<TLRPC.Message> messages) {
        try {
            MessagesStorage.getInstance().getDatabase().beginTransaction();
            SQLitePreparedStatement state2 = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO media_v2 VALUES(?, ?, ?, ?, ?)");
            for (TLRPC.Message message : messages) {
                if (canAddMessageToMedia(message)) {
                    state2.requery();
                    ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(message.getObjectSize());
                    message.serializeToStream(data);
                    state2.bindInteger(1, message.id);
                    state2.bindLong(2, uid);
                    state2.bindInteger(3, message.date);
                    state2.bindInteger(4, type);
                    state2.bindByteBuffer(5, data.buffer);
                    state2.step();
                    MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                }
            }
            state2.dispose();
            MessagesStorage.getInstance().getDatabase().commitTransaction();
        } catch (Exception e) {
            FileLog.e("tmessages", e);
        }
    }

    private static void putMediaDatabase(final long uid, final int type, final ArrayList<TLRPC.Message> messages) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                putMediaDatabaseInternal(uid, type, messages);
            }
        });
    }
}
