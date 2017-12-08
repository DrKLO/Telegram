/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger.query;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.StickersArchiveAlert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

@SuppressWarnings("unchecked")
public class StickersQuery {

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_MASK = 1;
    public static final int TYPE_FAVE = 2;

    private static ArrayList<TLRPC.TL_messages_stickerSet> stickerSets[] = new ArrayList[] {new ArrayList<>(), new ArrayList<>()};
    private static HashMap<Long, TLRPC.TL_messages_stickerSet> stickerSetsById = new HashMap<>();
    private static HashMap<Long, TLRPC.TL_messages_stickerSet> groupStickerSets = new HashMap<>();
    private static HashMap<String, TLRPC.TL_messages_stickerSet> stickerSetsByName = new HashMap<>();
    private static boolean loadingStickers[] = new boolean[2];
    private static boolean stickersLoaded[] = new boolean[2];
    private static int loadHash[] = new int[2];
    private static int loadDate[] = new int[2];

    private static int archivedStickersCount[] = new int[2];

    private static HashMap<Long, String> stickersByEmoji = new HashMap<>();
    private static HashMap<String, ArrayList<TLRPC.Document>> allStickers = new HashMap<>();

    private static ArrayList<TLRPC.Document> recentStickers[] = new ArrayList[] {new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    private static boolean loadingRecentStickers[] = new boolean[3];
    private static boolean recentStickersLoaded[] = new boolean[3];

    private static ArrayList<TLRPC.Document> recentGifs = new ArrayList<>();
    private static boolean loadingRecentGifs;
    private static boolean recentGifsLoaded;

    private static int loadFeaturedHash;
    private static int loadFeaturedDate;
    private static ArrayList<TLRPC.StickerSetCovered> featuredStickerSets = new ArrayList<>();
    private static HashMap<Long, TLRPC.StickerSetCovered> featuredStickerSetsById = new HashMap<>();
    private static ArrayList<Long> unreadStickerSets = new ArrayList<>();
    private static ArrayList<Long> readingStickerSets = new ArrayList<>();
    private static boolean loadingFeaturedStickers;
    private static boolean featuredStickersLoaded;

    public static void cleanup() {
        for (int a = 0; a < 3; a++) {
            recentStickers[a].clear();
            loadingRecentStickers[a] = false;
            recentStickersLoaded[a] = false;
        }
        for (int a = 0; a < 2; a++) {
            loadHash[a] = 0;
            loadDate[a] = 0;
            stickerSets[a].clear();
            loadingStickers[a] = false;
            stickersLoaded[a] = false;
        }
        loadFeaturedDate = 0;
        loadFeaturedHash = 0;
        allStickers.clear();
        stickersByEmoji.clear();
        featuredStickerSetsById.clear();
        featuredStickerSets.clear();
        unreadStickerSets.clear();
        recentGifs.clear();
        stickerSetsById.clear();
        stickerSetsByName.clear();
        loadingFeaturedStickers = false;
        featuredStickersLoaded = false;
        loadingRecentGifs = false;
        recentGifsLoaded = false;
    }

    public static void checkStickers(int type) {
        if (!loadingStickers[type] && (!stickersLoaded[type] || Math.abs(System.currentTimeMillis() / 1000 - loadDate[type]) >= 60 * 60)) {
            loadStickers(type, true, false);
        }
    }

    public static void checkFeaturedStickers() {
        if (!loadingFeaturedStickers && (!featuredStickersLoaded || Math.abs(System.currentTimeMillis() / 1000 - loadFeaturedDate) >= 60 * 60)) {
            loadFeaturesStickers(true, false);
        }
    }

    public static ArrayList<TLRPC.Document> getRecentStickers(int type) {
        return new ArrayList<>(recentStickers[type]);
    }

    public static ArrayList<TLRPC.Document> getRecentStickersNoCopy(int type) {
        return recentStickers[type];
    }

    public static boolean isStickerInFavorites(TLRPC.Document document) {
        for (int a = 0; a < recentStickers[TYPE_FAVE].size(); a++) {
            TLRPC.Document d = recentStickers[TYPE_FAVE].get(a);
            if (d.id == document.id && d.dc_id == document.dc_id) {
                return true;
            }
        }
        return false;
    }

    public static void addRecentSticker(final int type, TLRPC.Document document, int date, boolean remove) {
        boolean found = false;
        for (int a = 0; a < recentStickers[type].size(); a++) {
            TLRPC.Document image = recentStickers[type].get(a);
            if (image.id == document.id) {
                recentStickers[type].remove(a);
                if (!remove) {
                    recentStickers[type].add(0, image);
                }
                found = true;
            }
        }
        if (!found && !remove) {
            recentStickers[type].add(0, document);
        }
        int maxCount;
        if (type == TYPE_FAVE) {
            if (remove) {
                Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("RemovedFromFavorites", R.string.RemovedFromFavorites), Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(ApplicationLoader.applicationContext, LocaleController.getString("AddedToFavorites", R.string.AddedToFavorites), Toast.LENGTH_SHORT).show();
            }
            TLRPC.TL_messages_faveSticker req = new TLRPC.TL_messages_faveSticker();
            req.id = new TLRPC.TL_inputDocument();
            req.id.id = document.id;
            req.id.access_hash = document.access_hash;
            req.unfave = remove;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
            maxCount = MessagesController.getInstance().maxFaveStickersCount;
        } else {
            maxCount = MessagesController.getInstance().maxRecentStickersCount;
        }
        if (recentStickers[type].size() > maxCount || remove) {
            final TLRPC.Document old = remove ? document : recentStickers[type].remove(recentStickers[type].size() - 1);
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    int cacheType;
                    if (type == TYPE_IMAGE) {
                        cacheType = 3;
                    } else if (type == TYPE_MASK) {
                        cacheType = 4;
                    } else {
                        cacheType = 5;
                    }
                    try {
                        MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = " + cacheType).stepThis().dispose();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        }
        if (!remove) {
            ArrayList<TLRPC.Document> arrayList = new ArrayList<>();
            arrayList.add(document);
            processLoadedRecentDocuments(type, arrayList, false, date);
        }
        if (type == TYPE_FAVE) {
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.recentDocumentsDidLoaded, false, type);
        }
    }

    public static ArrayList<TLRPC.Document> getRecentGifs() {
        return new ArrayList<>(recentGifs);
    }

    public static void removeRecentGif(final TLRPC.Document document) {
        recentGifs.remove(document);
        TLRPC.TL_messages_saveGif req = new TLRPC.TL_messages_saveGif();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.unsave = true;
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + document.id + "' AND type = 2").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public static void addRecentGif(TLRPC.Document document, int date) {
        boolean found = false;
        for (int a = 0; a < recentGifs.size(); a++) {
            TLRPC.Document image = recentGifs.get(a);
            if (image.id == document.id) {
                recentGifs.remove(a);
                recentGifs.add(0, image);
                found = true;
            }
        }
        if (!found) {
            recentGifs.add(0, document);
        }
        if (recentGifs.size() > MessagesController.getInstance().maxRecentGifsCount) {
            final TLRPC.Document old = recentGifs.remove(recentGifs.size() - 1);
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        MessagesStorage.getInstance().getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = 2").stepThis().dispose();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        }
        ArrayList<TLRPC.Document> arrayList = new ArrayList<>();
        arrayList.add(document);
        processLoadedRecentDocuments(0, arrayList, true, date);
    }

    public static boolean isLoadingStickers(int type) {
        return loadingStickers[type];
    }

    public static TLRPC.TL_messages_stickerSet getStickerSetByName(String name) {
        return stickerSetsByName.get(name);
    }

    public static TLRPC.TL_messages_stickerSet getStickerSetById(Long id) {
        return stickerSetsById.get(id);
    }

    public static TLRPC.TL_messages_stickerSet getGroupStickerSetById(TLRPC.StickerSet stickerSet) {
        TLRPC.TL_messages_stickerSet set = stickerSetsById.get(stickerSet.id);
        if (set == null) {
            set = groupStickerSets.get(stickerSet.id);
            if (set == null || set.set == null) {
                loadGroupStickerSet(stickerSet, true);
            } else if (set.set.hash != stickerSet.hash) {
                loadGroupStickerSet(stickerSet, false);
            }
        }
        return set;
    }

    public static void putGroupStickerSet(TLRPC.TL_messages_stickerSet stickerSet) {
        groupStickerSets.put(stickerSet.set.id, stickerSet);
    }

    private static void loadGroupStickerSet(final TLRPC.StickerSet stickerSet, boolean cache) {
        if (cache) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        final TLRPC.TL_messages_stickerSet set;
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT document FROM web_recent_v3 WHERE id = 's_" + stickerSet.id + "'");
                        if (cursor.next() && !cursor.isNull(0)) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                set = TLRPC.TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                            } else {
                                set = null;
                            }
                        } else {
                            set = null;
                        }
                        cursor.dispose();
                        if (set == null || set.set == null || set.set.hash != stickerSet.hash) {
                            loadGroupStickerSet(stickerSet, false);
                        }
                        if (set != null && set.set != null) {
                            AndroidUtilities.runOnUIThread(new Runnable() {
                                @Override
                                public void run() {
                                    groupStickerSets.put(set.set.id, set);
                                    NotificationCenter.getInstance().postNotificationName(NotificationCenter.groupStickersDidLoaded, set.set.id);
                                }
                            });
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            });
        } else {
            TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
            req.stickerset = new TLRPC.TL_inputStickerSetID();
            req.stickerset.id = stickerSet.id;
            req.stickerset.access_hash = stickerSet.access_hash;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (response != null) {
                        final TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    SQLiteDatabase database = MessagesStorage.getInstance().getDatabase();
                                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO web_recent_v3 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                                    state.requery();
                                    state.bindString(1, "s_" + set.set.id);
                                    state.bindInteger(2, 6);
                                    state.bindString(3, "");
                                    state.bindString(4, "");
                                    state.bindString(5, "");
                                    state.bindInteger(6, 0);
                                    state.bindInteger(7, 0);
                                    state.bindInteger(8, 0);
                                    state.bindInteger(9, 0);
                                    NativeByteBuffer data = new NativeByteBuffer(set.getObjectSize());
                                    set.serializeToStream(data);
                                    state.bindByteBuffer(10, data);
                                    state.step();
                                    data.reuse();
                                    state.dispose();
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            }
                        });
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                groupStickerSets.put(set.set.id, set);
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.groupStickersDidLoaded, set.set.id);
                            }
                        });
                    }
                }
            });
        }
    }

    public static HashMap<String, ArrayList<TLRPC.Document>> getAllStickers() {
        return allStickers;
    }

    public static boolean canAddStickerToFavorites() {
        return !stickersLoaded[0] || stickerSets[0].size() >= 5 || !recentStickers[TYPE_FAVE].isEmpty();
    }

    public static ArrayList<TLRPC.TL_messages_stickerSet> getStickerSets(int type) {
        return stickerSets[type];
    }

    public static ArrayList<TLRPC.StickerSetCovered> getFeaturedStickerSets() {
        return featuredStickerSets;
    }

    public static ArrayList<Long> getUnreadStickerSets() {
        return unreadStickerSets;
    }

    public static boolean isStickerPackInstalled(long id) {
        return stickerSetsById.containsKey(id);
    }

    public static boolean isStickerPackUnread(long id) {
        return unreadStickerSets.contains(id);
    }

    public static boolean isStickerPackInstalled(String name) {
        return stickerSetsByName.containsKey(name);
    }

    public static String getEmojiForSticker(long id) {
        String value = stickersByEmoji.get(id);
        return value != null ? value : "";
    }

    private static int calcDocumentsHash(ArrayList<TLRPC.Document> arrayList) {
        if (arrayList == null) {
            return 0;
        }
        long acc = 0;
        for (int a = 0; a < Math.min(200, arrayList.size()); a++) {
            TLRPC.Document document = arrayList.get(a);
            if (document == null) {
                continue;
            }
            int high_id = (int) (document.id >> 32);
            int lower_id = (int) document.id;
            acc = ((acc * 20261) + 0x80000000L + high_id) % 0x80000000L;
            acc = ((acc * 20261) + 0x80000000L + lower_id) % 0x80000000L;
        }
        return (int) acc;
    }

    public static void loadRecents(final int type, final boolean gif, boolean cache, boolean force) {
        if (gif) {
            if (loadingRecentGifs) {
                return;
            }
            loadingRecentGifs = true;
            if (recentGifsLoaded) {
                cache = false;
            }
        } else {
            if (loadingRecentStickers[type]) {
                return;
            }
            loadingRecentStickers[type] = true;
            if (recentStickersLoaded[type]) {
                cache = false;
            }
        }
        if (cache) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        final int cacheType;
                        if (gif) {
                            cacheType = 2;
                        } else if (type == TYPE_IMAGE) {
                            cacheType = 3;
                        } else if (type == TYPE_MASK) {
                            cacheType = 4;
                        } else {
                            cacheType = 5;
                        }
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT document FROM web_recent_v3 WHERE type = " + cacheType + " ORDER BY date DESC");
                        final ArrayList<TLRPC.Document> arrayList = new ArrayList<>();
                        while (cursor.next()) {
                            if (!cursor.isNull(0)) {
                                NativeByteBuffer data = cursor.byteBufferValue(0);
                                if (data != null) {
                                    TLRPC.Document document = TLRPC.Document.TLdeserialize(data, data.readInt32(false), false);
                                    if (document != null) {
                                        arrayList.add(document);
                                    }
                                    data.reuse();
                                }
                            }
                        }
                        cursor.dispose();
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (gif) {
                                    recentGifs = arrayList;
                                    loadingRecentGifs = false;
                                    recentGifsLoaded = true;
                                } else {
                                    recentStickers[type] = arrayList;
                                    loadingRecentStickers[type] = false;
                                    recentStickersLoaded[type] = true;
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.recentDocumentsDidLoaded, gif, type);
                                loadRecents(type, gif, false, false);
                            }
                        });
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            });
        } else {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Activity.MODE_PRIVATE);
            if (!force) {
                long lastLoadTime;
                if (gif) {
                    lastLoadTime = preferences.getLong("lastGifLoadTime", 0);
                } else if (type == TYPE_IMAGE) {
                    lastLoadTime = preferences.getLong("lastStickersLoadTime", 0);
                } else if (type == TYPE_MASK) {
                    lastLoadTime = preferences.getLong("lastStickersLoadTimeMask", 0);
                } else {
                    lastLoadTime = preferences.getLong("lastStickersLoadTimeFavs", 0);
                }
                if (Math.abs(System.currentTimeMillis() - lastLoadTime) < 60 * 60 * 1000) {
                    if (gif) {
                        loadingRecentGifs = false;
                    } else {
                        loadingRecentStickers[type] = false;
                    }
                    return;
                }
            }
            if (gif) {
                TLRPC.TL_messages_getSavedGifs req = new TLRPC.TL_messages_getSavedGifs();
                req.hash = calcDocumentsHash(recentGifs);
                ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, TLRPC.TL_error error) {
                        ArrayList<TLRPC.Document> arrayList = null;
                        if (response instanceof TLRPC.TL_messages_savedGifs) {
                            TLRPC.TL_messages_savedGifs res = (TLRPC.TL_messages_savedGifs) response;
                            arrayList = res.gifs;
                        }
                        processLoadedRecentDocuments(type, arrayList, gif, 0);
                    }
                });
            } else {
                TLObject request;
                if (type == TYPE_FAVE) {
                    TLRPC.TL_messages_getFavedStickers req = new TLRPC.TL_messages_getFavedStickers();
                    req.hash = calcDocumentsHash(recentStickers[type]);
                    request = req;
                } else {
                    TLRPC.TL_messages_getRecentStickers req = new TLRPC.TL_messages_getRecentStickers();
                    req.hash = calcDocumentsHash(recentStickers[type]);
                    req.attached = type == TYPE_MASK;
                    request = req;
                }
                ConnectionsManager.getInstance().sendRequest(request, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        ArrayList<TLRPC.Document> arrayList = null;
                        if (type == TYPE_FAVE) {
                            if (response instanceof TLRPC.TL_messages_favedStickers) {
                                TLRPC.TL_messages_favedStickers res = (TLRPC.TL_messages_favedStickers) response;
                                arrayList = res.stickers;
                            }
                        } else {
                            if (response instanceof TLRPC.TL_messages_recentStickers) {
                                TLRPC.TL_messages_recentStickers res = (TLRPC.TL_messages_recentStickers) response;
                                arrayList = res.stickers;
                            }
                        }
                        processLoadedRecentDocuments(type, arrayList, gif, 0);
                    }
                });
            }
        }
    }

    private static void processLoadedRecentDocuments(final int type, final ArrayList<TLRPC.Document> documents, final boolean gif, final int date) {
        if (documents != null) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        SQLiteDatabase database = MessagesStorage.getInstance().getDatabase();
                        int maxCount;
                        if (gif) {
                            maxCount = MessagesController.getInstance().maxRecentGifsCount;
                        } else {
                            if (type == TYPE_FAVE) {
                                maxCount = MessagesController.getInstance().maxFaveStickersCount;
                            } else {
                                maxCount = MessagesController.getInstance().maxRecentStickersCount;
                            }
                        }
                        database.beginTransaction();
                        SQLitePreparedStatement state = database.executeFast("REPLACE INTO web_recent_v3 VALUES(?, ?, ?, ?, ?, ?, ?, ?, ?, ?)");
                        int count = documents.size();
                        int cacheType;
                        if (gif) {
                            cacheType = 2;
                        } else if (type == TYPE_IMAGE) {
                            cacheType = 3;
                        } else if (type == TYPE_MASK) {
                            cacheType = 4;
                        } else {
                            cacheType = 5;
                        }
                        for (int a = 0; a < count; a++) {
                            if (a == maxCount) {
                                break;
                            }
                            TLRPC.Document document = documents.get(a);
                            state.requery();
                            state.bindString(1, "" + document.id);
                            state.bindInteger(2, cacheType);
                            state.bindString(3, "");
                            state.bindString(4, "");
                            state.bindString(5, "");
                            state.bindInteger(6, 0);
                            state.bindInteger(7, 0);
                            state.bindInteger(8, 0);
                            state.bindInteger(9, date != 0 ? date : count - a);
                            NativeByteBuffer data = new NativeByteBuffer(document.getObjectSize());
                            document.serializeToStream(data);
                            state.bindByteBuffer(10, data);
                            state.step();
                            if (data != null) {
                                data.reuse();
                            }
                        }
                        state.dispose();
                        database.commitTransaction();
                        if (documents.size() >= maxCount) {
                            database.beginTransaction();
                            for (int a = maxCount; a < documents.size(); a++) {
                                database.executeFast("DELETE FROM web_recent_v3 WHERE id = '" + documents.get(a).id + "' AND type = " + cacheType).stepThis().dispose();
                            }
                            database.commitTransaction();
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        }
        if (date == 0) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    SharedPreferences.Editor editor = ApplicationLoader.applicationContext.getSharedPreferences("emoji", Activity.MODE_PRIVATE).edit();
                    if (gif) {
                        loadingRecentGifs = false;
                        recentGifsLoaded = true;
                        editor.putLong("lastGifLoadTime", System.currentTimeMillis()).commit();
                    } else {
                        loadingRecentStickers[type] = false;
                        recentStickersLoaded[type] = true;
                        if (type == TYPE_IMAGE) {
                            editor.putLong("lastStickersLoadTime", System.currentTimeMillis()).commit();
                        } else if (type == TYPE_MASK) {
                            editor.putLong("lastStickersLoadTimeMask", System.currentTimeMillis()).commit();
                        } else {
                            editor.putLong("lastStickersLoadTimeFavs", System.currentTimeMillis()).commit();
                        }
                    }
                    if (documents != null) {
                        if (gif) {
                            recentGifs = documents;
                        } else {
                            recentStickers[type] = documents;
                        }
                        NotificationCenter.getInstance().postNotificationName(NotificationCenter.recentDocumentsDidLoaded, gif, type);
                    } else {

                    }
                }
            });
        }
    }

    public static void reorderStickers(int type, final ArrayList<Long> order) {
        Collections.sort(stickerSets[type], new Comparator<TLRPC.TL_messages_stickerSet>() {
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
        loadHash[type] = calcStickersHash(stickerSets[type]);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded, type);
        StickersQuery.loadStickers(type, false, true);
    }

    public static void calcNewHash(int type) {
        loadHash[type] = calcStickersHash(stickerSets[type]);
    }

    public static void addNewStickerSet(final TLRPC.TL_messages_stickerSet set) {
        if (stickerSetsById.containsKey(set.set.id) || stickerSetsByName.containsKey(set.set.short_name)) {
            return;
        }
        int type = set.set.masks ? TYPE_MASK : TYPE_IMAGE;
        stickerSets[type].add(0, set);
        stickerSetsById.put(set.set.id, set);
        stickerSetsByName.put(set.set.short_name, set);
        HashMap<Long, TLRPC.Document> stickersById = new HashMap<>();
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
                TLRPC.Document sticker = stickersById.get(id);
                if (sticker != null) {
                    arrayList.add(sticker);
                }
            }
        }
        loadHash[type] = calcStickersHash(stickerSets[type]);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded, type);
        StickersQuery.loadStickers(type, false, true);
    }

    public static void loadFeaturesStickers(boolean cache, boolean force) {
        if (loadingFeaturedStickers) {
            return;
        }
        loadingFeaturedStickers = true;
        if (cache) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    ArrayList<TLRPC.StickerSetCovered> newStickerArray = null;
                    ArrayList<Long> unread = new ArrayList<>();
                    int date = 0;
                    int hash = 0;
                    SQLiteCursor cursor = null;
                    try {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT data, unread, date, hash FROM stickers_featured WHERE 1");
                        if (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                newStickerArray = new ArrayList<>();
                                int count = data.readInt32(false);
                                for (int a = 0; a < count; a++) {
                                    TLRPC.StickerSetCovered stickerSet = TLRPC.StickerSetCovered.TLdeserialize(data, data.readInt32(false), false);
                                    newStickerArray.add(stickerSet);
                                }
                                data.reuse();
                            }
                            data = cursor.byteBufferValue(1);
                            if (data != null) {
                                int count = data.readInt32(false);
                                for (int a = 0; a < count; a++) {
                                    unread.add(data.readInt64(false));
                                }
                                data.reuse();
                            }
                            date = cursor.intValue(2);
                            hash = calcFeaturedStickersHash(newStickerArray);
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    } finally {
                        if (cursor != null) {
                            cursor.dispose();
                        }
                    }
                    processLoadedFeaturedStickers(newStickerArray, unread, true, date, hash);
                }
            });
        } else {
            final TLRPC.TL_messages_getFeaturedStickers req = new TLRPC.TL_messages_getFeaturedStickers();
            req.hash = force ? 0 : loadFeaturedHash;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response instanceof TLRPC.TL_messages_featuredStickers) {
                                TLRPC.TL_messages_featuredStickers res = (TLRPC.TL_messages_featuredStickers) response;
                                processLoadedFeaturedStickers(res.sets, res.unread, false, (int) (System.currentTimeMillis() / 1000), res.hash);
                            } else {
                                processLoadedFeaturedStickers(null, null, false, (int) (System.currentTimeMillis() / 1000), req.hash);
                            }
                        }
                    });
                }
            });
        }
    }

    private static void processLoadedFeaturedStickers(final ArrayList<TLRPC.StickerSetCovered> res, final ArrayList<Long> unreadStickers, final boolean cache, final int date, final int hash) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                loadingFeaturedStickers = false;
                featuredStickersLoaded = true;
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
                                loadFeaturedHash = hash;
                            }
                            loadFeaturesStickers(false, false);
                        }
                    }, res == null && !cache ? 1000 : 0);
                    if (res == null) {
                        return;
                    }
                }
                if (res != null) {
                    try {
                        final ArrayList<TLRPC.StickerSetCovered> stickerSetsNew = new ArrayList<>();
                        final HashMap<Long, TLRPC.StickerSetCovered> stickerSetsByIdNew = new HashMap<>();

                        for (int a = 0; a < res.size(); a++) {
                            TLRPC.StickerSetCovered stickerSet = res.get(a);
                            stickerSetsNew.add(stickerSet);
                            stickerSetsByIdNew.put(stickerSet.set.id, stickerSet);
                        }

                        if (!cache) {
                            putFeaturedStickersToCache(stickerSetsNew, unreadStickers, date, hash);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                unreadStickerSets = unreadStickers;
                                featuredStickerSetsById = stickerSetsByIdNew;
                                featuredStickerSets = stickerSetsNew;
                                loadFeaturedHash = hash;
                                loadFeaturedDate = date;
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.featuredStickersDidLoaded);
                            }
                        });
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else if (!cache) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadFeaturedDate = date;
                        }
                    });
                    putFeaturedStickersToCache(null, null, date, 0);
                }
            }
        });
    }

    private static void putFeaturedStickersToCache(ArrayList<TLRPC.StickerSetCovered> stickers, final ArrayList<Long> unreadStickers, final int date, final int hash) {
        final ArrayList<TLRPC.StickerSetCovered> stickersFinal = stickers != null ? new ArrayList<>(stickers) : null;
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (stickersFinal != null) {
                        SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO stickers_featured VALUES(?, ?, ?, ?, ?)");
                        state.requery();
                        int size = 4;
                        for (int a = 0; a < stickersFinal.size(); a++) {
                            size += stickersFinal.get(a).getObjectSize();
                        }
                        NativeByteBuffer data = new NativeByteBuffer(size);
                        NativeByteBuffer data2 = new NativeByteBuffer(4 + unreadStickers.size() * 8);
                        data.writeInt32(stickersFinal.size());
                        for (int a = 0; a < stickersFinal.size(); a++) {
                            stickersFinal.get(a).serializeToStream(data);
                        }
                        data2.writeInt32(unreadStickers.size());
                        for (int a = 0; a < unreadStickers.size(); a++) {
                            data2.writeInt64(unreadStickers.get(a));
                        }
                        state.bindInteger(1, 1);
                        state.bindByteBuffer(2, data);
                        state.bindByteBuffer(3, data2);
                        state.bindInteger(4, date);
                        state.bindInteger(5, hash);
                        state.step();
                        data.reuse();
                        data2.reuse();
                        state.dispose();
                    } else {
                        SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("UPDATE stickers_featured SET date = ?");
                        state.requery();
                        state.bindInteger(1, date);
                        state.step();
                        state.dispose();
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private static int calcFeaturedStickersHash(ArrayList<TLRPC.StickerSetCovered> sets) {
        long acc = 0;
        for (int a = 0; a < sets.size(); a++) {
            TLRPC.StickerSet set = sets.get(a).set;
            if (set.archived) {
                continue;
            }
            int high_id = (int) (set.id >> 32);
            int lower_id = (int) set.id;
            acc = ((acc * 20261) + 0x80000000L + high_id) % 0x80000000L;
            acc = ((acc * 20261) + 0x80000000L + lower_id) % 0x80000000L;
            if (unreadStickerSets.contains(set.id)) {
                acc = ((acc * 20261) + 0x80000000L + 1) % 0x80000000L;
            }
        }
        return (int) acc;
    }

    public static void markFaturedStickersAsRead(boolean query) {
        if (unreadStickerSets.isEmpty()) {
            return;
        }
        unreadStickerSets.clear();
        loadFeaturedHash = calcFeaturedStickersHash(featuredStickerSets);
        NotificationCenter.getInstance().postNotificationName(NotificationCenter.featuredStickersDidLoaded);
        putFeaturedStickersToCache(featuredStickerSets, unreadStickerSets, loadFeaturedDate, loadFeaturedHash);
        if (query) {
            TLRPC.TL_messages_readFeaturedStickers req = new TLRPC.TL_messages_readFeaturedStickers();
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
    }

    public static int getFeaturesStickersHashWithoutUnread() {
        long acc = 0;
        for (int a = 0; a < featuredStickerSets.size(); a++) {
            TLRPC.StickerSet set = featuredStickerSets.get(a).set;
            if (set.archived) {
                continue;
            }
            int high_id = (int) (set.id >> 32);
            int lower_id = (int) set.id;
            acc = ((acc * 20261) + 0x80000000L + high_id) % 0x80000000L;
            acc = ((acc * 20261) + 0x80000000L + lower_id) % 0x80000000L;
        }
        return (int) acc;
    }

    public static void markFaturedStickersByIdAsRead(final long id) {
        if (!unreadStickerSets.contains(id) || readingStickerSets.contains(id)) {
            return;
        }
        readingStickerSets.add(id);
        TLRPC.TL_messages_readFeaturedStickers req = new TLRPC.TL_messages_readFeaturedStickers();
        req.id.add(id);
        ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                unreadStickerSets.remove(id);
                readingStickerSets.remove(id);
                loadFeaturedHash = calcFeaturedStickersHash(featuredStickerSets);
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.featuredStickersDidLoaded);
                putFeaturedStickersToCache(featuredStickerSets, unreadStickerSets, loadFeaturedDate, loadFeaturedHash);
            }
        }, 1000);
    }

    public static int getArchivedStickersCount(int type) {
        return archivedStickersCount[type];
    }

    public static void loadArchivedStickersCount(final int type, boolean cache) {
        if (cache) {
            SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
            int count = preferences.getInt("archivedStickersCount" + type, -1);
            if (count == -1) {
                loadArchivedStickersCount(type, false);
            } else {
                archivedStickersCount[type] = count;
                NotificationCenter.getInstance().postNotificationName(NotificationCenter.archivedStickersCountDidLoaded, type);
            }
        } else {
            TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
            req.limit = 0;
            req.masks = type == TYPE_MASK;
            int reqId = ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_messages_archivedStickers res = (TLRPC.TL_messages_archivedStickers) response;
                                archivedStickersCount[type] = res.count;
                                SharedPreferences preferences = ApplicationLoader.applicationContext.getSharedPreferences("Notifications", Activity.MODE_PRIVATE);
                                preferences.edit().putInt("archivedStickersCount" + type, res.count).commit();
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.archivedStickersCountDidLoaded, type);
                            }
                        }
                    });
                }
            });
        }
    }

    public static void loadStickers(final int type, boolean cache, boolean force) {
        if (loadingStickers[type]) {
            return;
        }
        loadArchivedStickersCount(type, cache);
        loadingStickers[type] = true;
        if (cache) {
            MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = null;
                    int date = 0;
                    int hash = 0;
                    SQLiteCursor cursor = null;
                    try {
                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT data, date, hash FROM stickers_v2 WHERE id = " + (type + 1));
                        if (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                newStickerArray = new ArrayList<>();
                                int count = data.readInt32(false);
                                for (int a = 0; a < count; a++) {
                                    TLRPC.TL_messages_stickerSet stickerSet = TLRPC.TL_messages_stickerSet.TLdeserialize(data, data.readInt32(false), false);
                                    newStickerArray.add(stickerSet);
                                }
                                data.reuse();
                            }
                            date = cursor.intValue(1);
                            hash = calcStickersHash(newStickerArray);
                        }
                    } catch (Throwable e) {
                        FileLog.e(e);
                    } finally {
                        if (cursor != null) {
                            cursor.dispose();
                        }
                    }
                    processLoadedStickers(type, newStickerArray, true, date, hash);
                }
            });
        } else {
            TLObject req;
            final int hash;
            if (type == TYPE_IMAGE) {
                req = new TLRPC.TL_messages_getAllStickers();
                hash = ((TLRPC.TL_messages_getAllStickers) req).hash = force ? 0 : loadHash[type];
            } else {
                req = new TLRPC.TL_messages_getMaskStickers();
                hash = ((TLRPC.TL_messages_getMaskStickers) req).hash = force ? 0 : loadHash[type];
            }
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response instanceof TLRPC.TL_messages_allStickers) {
                                final TLRPC.TL_messages_allStickers res = (TLRPC.TL_messages_allStickers) response;
                                final ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = new ArrayList<>();
                                if (res.sets.isEmpty()) {
                                    processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash);
                                } else {
                                    final HashMap<Long, TLRPC.TL_messages_stickerSet> newStickerSets = new HashMap<>();
                                    for (int a = 0; a < res.sets.size(); a++) {
                                        final TLRPC.StickerSet stickerSet = res.sets.get(a);

                                        TLRPC.TL_messages_stickerSet oldSet = stickerSetsById.get(stickerSet.id);
                                        if (oldSet != null && oldSet.set.hash == stickerSet.hash) {
                                            oldSet.set.archived = stickerSet.archived;
                                            oldSet.set.installed = stickerSet.installed;
                                            oldSet.set.official = stickerSet.official;
                                            newStickerSets.put(oldSet.set.id, oldSet);
                                            newStickerArray.add(oldSet);

                                            if (newStickerSets.size() == res.sets.size()) {
                                                processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash);
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
                                                            for (int a = 0; a < newStickerArray.size(); a++) {
                                                                if (newStickerArray.get(a) == null) {
                                                                    newStickerArray.remove(a);
                                                                }
                                                            }
                                                            processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash);
                                                        }
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }
                            } else {
                                processLoadedStickers(type, null, false, (int) (System.currentTimeMillis() / 1000), hash);
                            }
                        }
                    });
                }
            });
        }
    }

    private static void putStickersToCache(final int type, ArrayList<TLRPC.TL_messages_stickerSet> stickers, final int date, final int hash) {
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
                        state.bindInteger(1, type == TYPE_IMAGE ? 1 : 2);
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
                    FileLog.e(e);
                }
            }
        });
    }

    public static String getStickerSetName(long setId) {
        TLRPC.TL_messages_stickerSet stickerSet = stickerSetsById.get(setId);
        if (stickerSet != null) {
            return stickerSet.set.short_name;

        }
        TLRPC.StickerSetCovered stickerSetCovered = featuredStickerSetsById.get(setId);
        if (stickerSetCovered != null) {
            return stickerSetCovered.set.short_name;
        }
        return null;
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
            if (set.archived) {
                continue;
            }
            acc = ((acc * 20261) + 0x80000000L + set.hash) % 0x80000000L;
        }
        return (int) acc;
    }

    private static void processLoadedStickers(final int type, final ArrayList<TLRPC.TL_messages_stickerSet> res, final boolean cache, final int date, final int hash) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                loadingStickers[type] = false;
                stickersLoaded[type] = true;
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
                                loadHash[type] = hash;
                            }
                            loadStickers(type, false, false);
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
                            if (!stickerSet.set.archived) {
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
                                        TLRPC.Document sticker = stickersByIdNew.get(id);
                                        if (sticker != null) {
                                            arrayList.add(sticker);
                                        }
                                    }
                                }
                            }
                        }

                        if (!cache) {
                            putStickersToCache(type, stickerSetsNew, date, hash);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                for (int a = 0; a < stickerSets[type].size(); a++) {
                                    TLRPC.StickerSet set = stickerSets[type].get(a).set;
                                    stickerSetsById.remove(set.id);
                                    stickerSetsByName.remove(set.short_name);
                                }
                                stickerSetsById.putAll(stickerSetsByIdNew);
                                stickerSetsByName.putAll(stickerSetsByNameNew);
                                stickerSets[type] = stickerSetsNew;
                                loadHash[type] = hash;
                                loadDate[type] = date;
                                if (type == TYPE_IMAGE) {
                                    allStickers = allStickersNew;
                                    stickersByEmoji = stickersByEmojiNew;
                                }
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded, type);
                            }
                        });
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                } else if (!cache) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadDate[type] = date;
                        }
                    });
                    putStickersToCache(type, null, date, 0);
                }
            }
        });
    }

    public static void removeStickersSet(final Context context, final TLRPC.StickerSet stickerSet, final int hide, final BaseFragment baseFragment, final boolean showSettings) {
        final int type = stickerSet.masks ? TYPE_MASK : TYPE_IMAGE;
        TLRPC.TL_inputStickerSetID stickerSetID = new TLRPC.TL_inputStickerSetID();
        stickerSetID.access_hash = stickerSet.access_hash;
        stickerSetID.id = stickerSet.id;
        if (hide != 0) {
            stickerSet.archived = hide == 1;
            for (int a = 0; a < stickerSets[type].size(); a++) {
                TLRPC.TL_messages_stickerSet set = stickerSets[type].get(a);
                if (set.set.id == stickerSet.id) {
                    stickerSets[type].remove(a);
                    if (hide == 2) {
                        stickerSets[type].add(0, set);
                    } else {
                        stickerSetsById.remove(set.set.id);
                        stickerSetsByName.remove(set.set.short_name);
                    }
                    break;
                }
            }
            loadHash[type] = calcStickersHash(stickerSets[type]);
            putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type]);
            NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded, type);
            TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
            req.stickerset = stickerSetID;
            req.archived = hide == 1;
            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                                NotificationCenter.getInstance().postNotificationName(NotificationCenter.needReloadArchivedStickers, type);
                                if (hide != 1 && baseFragment != null && baseFragment.getParentActivity() != null) {
                                    StickersArchiveAlert alert = new StickersArchiveAlert(baseFragment.getParentActivity(), showSettings ? baseFragment : null, ((TLRPC.TL_messages_stickerSetInstallResultArchive) response).sets);
                                    baseFragment.showDialog(alert.create());
                                }
                            }
                        }
                    });
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadStickers(type, false, false);
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
                                    if (stickerSet.masks) {
                                        Toast.makeText(context, LocaleController.getString("MasksRemoved", R.string.MasksRemoved), Toast.LENGTH_SHORT).show();
                                    } else {
                                        Toast.makeText(context, LocaleController.getString("StickersRemoved", R.string.StickersRemoved), Toast.LENGTH_SHORT).show();
                                    }
                                } else {
                                    Toast.makeText(context, LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                            loadStickers(type, false, true);
                        }
                    });
                }
            });
        }
    }
}
