/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.android.query;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.HashMap;

public class StickersQuery {

    private static String hash;
    private static int loadDate;
    private static ArrayList<TLRPC.Document> stickers = new ArrayList<>();
    private static HashMap<String, ArrayList<TLRPC.Document>> allStickers = new HashMap<>();
    private static boolean loadingStickers;

    public static void checkStickers() {
        if (!loadingStickers && (allStickers.isEmpty() || loadDate < (System.currentTimeMillis() / 1000 - 60 * 60))) {
            loadStickers(true);
        }
    }

    public static ArrayList<TLRPC.Document> getStickers() {
        return stickers;
    }

    private static void loadStickers(boolean cache) {
        if (loadingStickers) {
            return;
        }
        loadingStickers = true;
        if (cache) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    TLRPC.messages_AllStickers result = null;
                    int date = 0;
                    try {
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT data, date FROM stickers WHERE 1");
                        ArrayList<TLRPC.User> loadedUsers = new ArrayList<>();
                        if (cursor.next()) {
                            ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(cursor.byteArrayLength(0));
                            if (data != null && cursor.byteBufferValue(0, data.buffer) != 0) {
                                result = TLRPC.messages_AllStickers.TLdeserialize(data, data.readInt32(false), false);
                            }
                            date = cursor.intValue(1);
                            MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                        }
                        cursor.dispose();
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                    processLoadedStickers(result, true, date);
                }
            });
        } else {
            TLRPC.TL_messages_getAllStickers req = new TLRPC.TL_messages_getAllStickers();
            req.hash = hash;
            if (req.hash == null) {
                req.hash = "";
            }
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            processLoadedStickers((TLRPC.messages_AllStickers) response, false, (int) (System.currentTimeMillis() / 1000));
                        }
                    });
                }
            });
        }
    }

    private static void putStickersToCache(final TLRPC.TL_messages_allStickers stickers) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO stickers VALUES(?, ?, ?)");
                    state.requery();
                    ByteBufferDesc data = MessagesStorage.getInstance().getBuffersStorage().getFreeBuffer(stickers.getObjectSize());
                    stickers.serializeToStream(data);
                    state.bindInteger(1, 1);
                    state.bindByteBuffer(2, data.buffer);
                    state.bindInteger(3, (int) (System.currentTimeMillis() / 1000));
                    state.step();
                    MessagesStorage.getInstance().getBuffersStorage().reuseFreeBuffer(data);
                    state.dispose();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    private static void processLoadedStickers(final TLRPC.messages_AllStickers res, final boolean cache, final int date) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                loadingStickers = false;
            }
        });
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if ((res == null || date < (int) (System.currentTimeMillis() / 1000 - 60 * 60)) && cache) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadStickers(false);
                        }
                    });
                    if (res == null) {
                        return;
                    }
                }
                if (res instanceof TLRPC.TL_messages_allStickers) {
                    if (!cache) {
                        putStickersToCache((TLRPC.TL_messages_allStickers) res);
                    }
                    HashMap<Long, TLRPC.Document> documents = new HashMap<>();
                    for (TLRPC.Document document : res.documents) {
                        if (document == null) {
                            continue;
                        }
                        documents.put(document.id, document);
                        if (document.thumb != null && document.thumb.location != null) {
                            document.thumb.location.ext = "webp";
                        }
                    }
                    final HashMap<String, ArrayList<TLRPC.Document>> result = new HashMap<>();
                    for (TLRPC.TL_stickerPack stickerPack : res.packs) {
                        if (stickerPack != null && stickerPack.emoticon != null) {
                            stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "");
                            ArrayList<TLRPC.Document> arrayList = result.get(stickerPack.emoticon);
                            for (Long id : stickerPack.documents) {
                                TLRPC.Document document = documents.get(id);
                                if (document != null) {
                                    if (arrayList == null) {
                                        arrayList = new ArrayList<>();
                                        result.put(stickerPack.emoticon, arrayList);
                                    }
                                    arrayList.add(document);
                                }
                            }
                        }
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            allStickers = result;
                            stickers = res.documents;
                            hash = res.hash;
                            loadDate = date;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded);
                        }
                    });
                }
            }
        });
    }
}
