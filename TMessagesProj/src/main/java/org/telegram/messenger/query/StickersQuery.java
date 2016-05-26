/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.messenger.query;

import android.content.Context;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.messenger.Utilities;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class StickersQuery {

    private static int loadHash;
    private static int loadDate;
    private static ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
    private static HashMap<Long, TLRPC.TL_messages_stickerSet> stickerSetsById = new HashMap<>();
    private static HashMap<String, TLRPC.TL_messages_stickerSet> stickerSetsByName = new HashMap<>();
    private static HashMap<Long, String> stickersByEmoji = new HashMap<>();
    private static HashMap<Long, TLRPC.Document> stickersById = new HashMap<>();
    private static HashMap<String, ArrayList<TLRPC.Document>> allStickers = new HashMap<>();

    private static boolean loadingStickers;
    private static boolean stickersLoaded;

    public static void cleanup() {
        loadHash = 0;
        loadDate = 0;
        allStickers.clear();
        stickerSets.clear();
        stickersByEmoji.clear();
        stickerSetsById.clear();
        stickerSetsByName.clear();
        loadingStickers = false;
        stickersLoaded = false;
    }

    public static void checkStickers() {
        if (!loadingStickers && (!stickersLoaded || Math.abs(System.currentTimeMillis() / 1000 - loadDate) >= 60 * 60)) {
            loadStickers(true, false);
        }
    }

    public static boolean isLoadingStickers() {
        return loadingStickers;
    }

    public static TLRPC.Document getStickerById(long id) {
        TLRPC.Document document = stickersById.get(id);
        if (document != null) {
            long setId = getStickerSetId(document);
            TLRPC.TL_messages_stickerSet stickerSet = stickerSetsById.get(setId);
            if (stickerSet != null && stickerSet.set.disabled) {
                return null;
            }
        }
        return document;
    }

    public static TLRPC.TL_messages_stickerSet getStickerSetByName(String name) {
        return stickerSetsByName.get(name);
    }

    public static TLRPC.TL_messages_stickerSet getStickerSetById(Long id) {
        return stickerSetsById.get(id);
    }

    public static HashMap<String, ArrayList<TLRPC.Document>> getAllStickers() {
        return allStickers;
    }

    public static ArrayList<TLRPC.TL_messages_stickerSet> getStickerSets() {
        return stickerSets;
    }

    public static boolean isStickerPackInstalled(long id) {
        return stickerSetsById.containsKey(id);
    }

    public static boolean isStickerPackInstalled(String name) {
        return stickerSetsByName.containsKey(name);
    }

    public static String getEmojiForSticker(long id) {
        String value = stickersByEmoji.get(id);
        return value != null ? value : "";
    }

    public static void reorderStickers(final ArrayList<Long> order) {
        Collections.sort(stickerSets, new Comparator<TLRPC.TL_messages_stickerSet>() {
            @Override
            public int compare(TLRPC.TL_messages_stickerSet lhs, TLRPC.TL_messages_stickerSet rhs) {
                int index1 = order.indexOf(lhs.set.id);
                int index2 = order.indexOf(rhs.set.id);
                if (index1 > index2) {
                    return 1;
                } else if (index1 < index2) {
                    return -1;
                }
                return 0;
            }
        });
        loadHash = calcStickersHash(stickerSets);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded);
        StickersQuery.loadStickers(false, true);
    }

    public static void calcNewHash() {
        loadHash = calcStickersHash(stickerSets);
    }

    public static void addNewStickerSet(final TLRPC.TL_messages_stickerSet set) {
        if (stickerSetsById.containsKey(set.set.id) || stickerSetsByName.containsKey(set.set.short_name)) {
            return;
        }
        stickerSets.add(0, set);
        stickerSetsById.put(set.set.id, set);
        stickerSetsByName.put(set.set.short_name, set);
        for (int a = 0; a < set.documents.size(); a++) {
            TLRPC.Document document = set.documents.get(a);
            stickersById.put(document.id, document);
        }
        for (int a = 0; a < set.packs.size(); a++) {
            TLRPC.TL_stickerPack stickerPack = set.packs.get(a);
            stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "");
            ArrayList<TLRPC.Document> arrayList = allStickers.get(stickerPack.emoticon);
            if (arrayList == null) {
                arrayList = new ArrayList<>();
                allStickers.put(stickerPack.emoticon, arrayList);
            }
            for (int c = 0; c < stickerPack.documents.size(); c++) {
                Long id = stickerPack.documents.get(c);
                if (!stickersByEmoji.containsKey(id)) {
                    stickersByEmoji.put(id, stickerPack.emoticon);
                }
                arrayList.add(stickersById.get(id));
            }
        }
        loadHash = calcStickersHash(stickerSets);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded);
        StickersQuery.loadStickers(false, true);
    }

    public static void loadStickers(boolean cache, boolean force) {
        if (loadingStickers) {
            return;
        }
        loadingStickers = true;
        if (cache) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = null;
                    int date = 0;
                    int hash = 0;
                    SQLiteCursor cursor = null;
                    try {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT data, date, hash FROM stickers_v2 WHERE 1");
                        if (cursor.next()) {
                            NativeByteBuffer data = new NativeByteBuffer(cursor.byteArrayLength(0));
                            if (data != null && cursor.byteBufferValue(0, data) != 0) {
                                newStickerArray = new ArrayList<>();
                                int count = data.readInt32(false);
                                for (int a = 0; a < count; a++) {
                                    TLRPC.TL_messages_stickerSet stickerSet = TLRPC.TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false);
                                    newStickerArray.add(stickerSet);
                                }
                            }
                            date = cursor.intValue(1);
                            hash = calcStickersHash(newStickerArray);
                            data.reuse();
                        }
                    } catch (Throwable e) {
                        FileLog.e("tmessages", e);
                    } finally {
                        if (cursor != null) {
                            cursor.dispose();
                        }
                    }
                    processLoadedStickers(newStickerArray, true, date, hash);
                }
            });
        } else {
            final TLRPC.TL_messages_getAllStickers req = new TLRPC.TL_messages_getAllStickers();
            req.hash = force ? 0 : loadHash;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response instanceof TLRPC.TL_messages_allStickers) {
                                final HashMap<Long, TLRPC.TL_messages_stickerSet> newStickerSets = new HashMap<>();
                                final ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = new ArrayList<>();
                                final TLRPC.TL_messages_allStickers res = (TLRPC.TL_messages_allStickers) response;

                                for (int a = 0; a < res.sets.size(); a++) {
                                    final TLRPC.StickerSet stickerSet = res.sets.get(a);

                                    TLRPC.TL_messages_stickerSet oldSet = stickerSetsById.get(stickerSet.id);
                                    if (oldSet != null && oldSet.set.hash == stickerSet.hash) {
                                        oldSet.set.disabled = stickerSet.disabled;
                                        oldSet.set.installed = stickerSet.installed;
                                        oldSet.set.official = stickerSet.official;
                                        newStickerSets.put(oldSet.set.id, oldSet);
                                        newStickerArray.add(oldSet);

                                        if (newStickerSets.size() == res.sets.size()) {
                                            processLoadedStickers(newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash);
                                        }
                                        continue;
                                    }

                                    newStickerArray.add(null);
                                    final int index = a;

                                    TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
                                    req.stickerset = new TLRPC.TL_inputStickerSetID();
                                    req.stickerset.id = stickerSet.id;
                                    req.stickerset.access_hash = stickerSet.access_hash;

                                    ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                        @Override
                                        public void run(final TLObject response, final TLRPC.TL_error error) {
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    TLRPC.TL_messages_stickerSet res1 = (TLRPC.TL_messages_stickerSet) response;
                                                    newStickerArray.set(index, res1);
                                                    newStickerSets.put(stickerSet.id, res1);
                                                    if (newStickerSets.size() == res.sets.size()) {
                                                        processLoadedStickers(newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash);
                                                    }
                                                }
                                            });
                                        }
                                    });
                                }
                            } else {
                                processLoadedStickers(null, false, (int) (System.currentTimeMillis() / 1000), req.hash);
                            }
                        }
                    });
                }
            });
        }
    }

    private static void putStickersToCache(ArrayList<TLRPC.TL_messages_stickerSet> stickers, final int date, final int hash) {
        final ArrayList<TLRPC.TL_messages_stickerSet> stickersFinal = stickers != null ? new ArrayList<>(stickers) : null;
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (stickersFinal != null) {
                        SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO stickers_v2 VALUES(?, ?, ?, ?)");
                        state.requery();
                        int size = 4;
                        for (int a = 0; a < stickersFinal.size(); a++) {
                            size += stickersFinal.get(a).getObjectSize();
                        }
                        NativeByteBuffer data = new NativeByteBuffer(size);
                        data.writeInt32(stickersFinal.size());
                        for (int a = 0; a < stickersFinal.size(); a++) {
                            stickersFinal.get(a).serializeToStream(data);
                        }
                        state.bindInteger(1, 1);
                        state.bindByteBuffer(2, data);
                        state.bindInteger(3, date);
                        state.bindInteger(4, hash);
                        state.step();
                        data.reuse();
                        state.dispose();
                    } else {
                        SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("UPDATE stickers_v2 SET date = ?");
                        state.requery();
                        state.bindInteger(1, date);
                        state.step();
                        state.dispose();
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public static String getStickerSetName(long setId) {
        TLRPC.TL_messages_stickerSet stickerSet = stickerSetsById.get(setId);
        return stickerSet != null ? stickerSet.set.short_name : null;
    }

    public static long getStickerSetId(TLRPC.Document document) {
        for (int a = 0; a < document.attributes.size(); a++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(a);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetID) {
                    return attribute.stickerset.id;
                }
                break;
            }
        }
        return -1;
    }

    private static int calcStickersHash(ArrayList<TLRPC.TL_messages_stickerSet> sets) {
        long acc = 0;
        for (int a = 0; a < sets.size(); a++) {
            TLRPC.StickerSet set = sets.get(a).set;
            if (set.disabled) {
                continue;
            }
            acc = ((acc * 20261) + 0x80000000L + set.hash) % 0x80000000L;
        }
        return (int) acc;
    }

    private static void processLoadedStickers(final ArrayList<TLRPC.TL_messages_stickerSet> res, final boolean cache, final int date, final int hash) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                loadingStickers = false;
                stickersLoaded = true;
            }
        });
        Utilities.stageQueue.postRunnable(new Runnable() {
            @Override
            public void run() {
                if (cache && (res == null || Math.abs(System.currentTimeMillis() / 1000 - date) >= 60 * 60) || !cache && res == null && hash == 0) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (res != null && hash != 0) {
                                loadHash = hash;
                            }
                            loadStickers(false, false);
                        }
                    }, res == null && !cache ? 1000 : 0);
                    if (res == null) {
                        return;
                    }
                }
                if (res != null) {
                    try {
                        final ArrayList<TLRPC.TL_messages_stickerSet> stickerSetsNew = new ArrayList<>();
                        final HashMap<Long, TLRPC.TL_messages_stickerSet> stickerSetsByIdNew = new HashMap<>();
                        final HashMap<String, TLRPC.TL_messages_stickerSet> stickerSetsByNameNew = new HashMap<>();
                        final HashMap<Long, String> stickersByEmojiNew = new HashMap<>();
                        final HashMap<Long, TLRPC.Document> stickersByIdNew = new HashMap<>();
                        final HashMap<String, ArrayList<TLRPC.Document>> allStickersNew = new HashMap<>();

                        for (int a = 0; a < res.size(); a++) {
                            TLRPC.TL_messages_stickerSet stickerSet = res.get(a);
                            if (stickerSet == null) {
                                continue;
                            }
                            stickerSetsNew.add(stickerSet);
                            stickerSetsByIdNew.put(stickerSet.set.id, stickerSet);
                            stickerSetsByNameNew.put(stickerSet.set.short_name, stickerSet);

                            for (int b = 0; b < stickerSet.documents.size(); b++) {
                                TLRPC.Document document = stickerSet.documents.get(b);
                                if (document == null || document instanceof TLRPC.TL_documentEmpty) {
                                    continue;
                                }
                                stickersByIdNew.put(document.id, document);
                            }
                            if (!stickerSet.set.disabled) {
                                for (int b = 0; b < stickerSet.packs.size(); b++) {
                                    TLRPC.TL_stickerPack stickerPack = stickerSet.packs.get(b);
                                    if (stickerPack == null || stickerPack.emoticon == null) {
                                        continue;
                                    }
                                    stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "");
                                    ArrayList<TLRPC.Document> arrayList = allStickersNew.get(stickerPack.emoticon);
                                    if (arrayList == null) {
                                        arrayList = new ArrayList<>();
                                        allStickersNew.put(stickerPack.emoticon, arrayList);
                                    }
                                    for (int c = 0; c < stickerPack.documents.size(); c++) {
                                        Long id = stickerPack.documents.get(c);
                                        if (!stickersByEmojiNew.containsKey(id)) {
                                            stickersByEmojiNew.put(id, stickerPack.emoticon);
                                        }
                                        arrayList.add(stickersByIdNew.get(id));
                                    }
                                }
                            }
                        }

                        if (!cache) {
                            putStickersToCache(stickerSetsNew, date, hash);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                stickersById = stickersByIdNew;
                                stickerSetsById = stickerSetsByIdNew;
                                stickerSetsByName = stickerSetsByNameNew;
                                stickerSets = stickerSetsNew;
                                allStickers = allStickersNew;
                                stickersByEmoji = stickersByEmojiNew;
                                loadHash = hash;
                                loadDate = date;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded);
                            }
                        });
                    } catch (Throwable e) {
                        FileLog.e("tmessages", e);
                    }
                } else if (!cache) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadDate = date;
                        }
                    });
                    putStickersToCache(null, date, 0);
                }
            }
        });
    }

    public static void removeStickersSet(final Context context, TLRPC.StickerSet stickerSet, int hide) {
        TLRPC.TL_inputStickerSetID stickerSetID = new TLRPC.TL_inputStickerSetID();
        stickerSetID.access_hash = stickerSet.access_hash;
        stickerSetID.id = stickerSet.id;
        if (hide != 0) {
            stickerSet.disabled = hide == 1;
            for (int a = 0; a < stickerSets.size(); a++) {
                TLRPC.TL_messages_stickerSet set = stickerSets.get(a);
                if (set.set.id == stickerSet.id) {
                    stickerSets.remove(a);
                    if (hide == 2) {
                        stickerSets.add(0, set);
                    } else {
                        for (int b = stickerSets.size() - 1; b >= 0; b--) {
                            if (stickerSets.get(b).set.disabled) {
                                continue;
                            }
                            stickerSets.add(b + 1, set);
                            break;
                        }
                    }
                    break;
                }
            }
            loadHash = calcStickersHash(stickerSets);
            putStickersToCache(stickerSets, loadDate, loadHash);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded);
            TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
            req.stickerset = stickerSetID;
            req.disabled = hide == 1;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadStickers(false, false);
                        }
                    }, 1000);
                }
            });
        } else {
            TLRPC.TL_messages_uninstallStickerSet req = new TLRPC.TL_messages_uninstallStickerSet();
            req.stickerset = stickerSetID;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                if (error == null) {
                                    Toast.makeText(context, LocaleController.getString("StickersRemoved", R.string.StickersRemoved), Toast.LENGTH_SHORT).show();
                                } else {
                                    Toast.makeText(context, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                FileLog.e("tmessages", e);
                            }
                            loadStickers(false, true);
                        }
                    });
                }
            });
        }
    }
}
