/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.messenger;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.SpannedString;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLiteDatabase;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.messenger.support.SparseLongArray;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.NativeByteBuffer;
import org.telegram.tgnet.RequestDelegate;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.StickersArchiveAlert;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanReplacement;
import org.telegram.ui.Components.URLSpanUserMention;
import org.telegram.ui.LaunchActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@SuppressWarnings("unchecked")
public class DataQuery {

    private int currentAccount;
    private static volatile DataQuery[] Instance = new DataQuery[3];
    public static DataQuery getInstance(int num) {
        DataQuery localInstance = Instance[num];
        if (localInstance == null) {
            synchronized (DataQuery.class) {
                localInstance = Instance[num];
                if (localInstance == null) {
                    Instance[num] = localInstance = new DataQuery(num);
                }
            }
        }
        return localInstance;
    }

    public DataQuery(int num) {
        currentAccount = num;

        if (currentAccount == 0) {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("drafts", Activity.MODE_PRIVATE);
        } else {
            preferences = ApplicationLoader.applicationContext.getSharedPreferences("drafts" + currentAccount, Activity.MODE_PRIVATE);
        }
        Map<String, ?> values = preferences.getAll();
        for (Map.Entry<String, ?> entry : values.entrySet()) {
            try {
                String key = entry.getKey();
                long did = Utilities.parseLong(key);
                byte[] bytes = Utilities.hexToBytes((String) entry.getValue());
                SerializedData serializedData = new SerializedData(bytes);
                if (key.startsWith("r_")) {
                    TLRPC.Message message = TLRPC.Message.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    message.readAttachPath(serializedData, UserConfig.getInstance(currentAccount).clientUserId);
                    if (message != null) {
                        draftMessages.put(did, message);
                    }
                } else {
                    TLRPC.DraftMessage draftMessage = TLRPC.DraftMessage.TLdeserialize(serializedData, serializedData.readInt32(true), true);
                    if (draftMessage != null) {
                        drafts.put(did, draftMessage);
                    }
                }
                serializedData.cleanup();
            } catch (Exception e) {
                //igonre
            }
        }
    }

    public static final int TYPE_IMAGE = 0;
    public static final int TYPE_MASK = 1;
    public static final int TYPE_FAVE = 2;
    public static final int TYPE_FEATURED = 3;

    private ArrayList<TLRPC.TL_messages_stickerSet> stickerSets[] = new ArrayList[] {new ArrayList<>(), new ArrayList<>(), new ArrayList(0), new ArrayList()};
    private LongSparseArray<TLRPC.TL_messages_stickerSet> stickerSetsById = new LongSparseArray<>();
    private LongSparseArray<TLRPC.TL_messages_stickerSet> installedStickerSetsById = new LongSparseArray<>();
    private LongSparseArray<TLRPC.TL_messages_stickerSet> groupStickerSets = new LongSparseArray<>();
    private HashMap<String, TLRPC.TL_messages_stickerSet> stickerSetsByName = new HashMap<>();
    private boolean loadingStickers[] = new boolean[4];
    private boolean stickersLoaded[] = new boolean[4];
    private int loadHash[] = new int[4];
    private int loadDate[] = new int[4];

    private int archivedStickersCount[] = new int[2];

    private LongSparseArray<String> stickersByEmoji = new LongSparseArray<>();
    private HashMap<String, ArrayList<TLRPC.Document>> allStickers = new HashMap<>();
    private HashMap<String, ArrayList<TLRPC.Document>> allStickersFeatured = new HashMap<>();

    private ArrayList<TLRPC.Document> recentStickers[] = new ArrayList[] {new ArrayList<>(), new ArrayList<>(), new ArrayList<>()};
    private boolean loadingRecentStickers[] = new boolean[3];
    private boolean recentStickersLoaded[] = new boolean[3];

    private ArrayList<TLRPC.Document> recentGifs = new ArrayList<>();
    private boolean loadingRecentGifs;
    private boolean recentGifsLoaded;

    private int loadFeaturedHash;
    private int loadFeaturedDate;
    private ArrayList<TLRPC.StickerSetCovered> featuredStickerSets = new ArrayList<>();
    private LongSparseArray<TLRPC.StickerSetCovered> featuredStickerSetsById = new LongSparseArray<>();
    private ArrayList<Long> unreadStickerSets = new ArrayList<>();
    private ArrayList<Long> readingStickerSets = new ArrayList<>();
    private boolean loadingFeaturedStickers;
    private boolean featuredStickersLoaded;

    public void cleanup() {
        for (int a = 0; a < 3; a++) {
            recentStickers[a].clear();
            loadingRecentStickers[a] = false;
            recentStickersLoaded[a] = false;
        }
        for (int a = 0; a < 4; a++) {
            loadHash[a] = 0;
            loadDate[a] = 0;
            stickerSets[a].clear();
            loadingStickers[a] = false;
            stickersLoaded[a] = false;
        }
        featuredStickerSets.clear();
        loadFeaturedDate = 0;
        loadFeaturedHash = 0;
        allStickers.clear();
        allStickersFeatured.clear();
        stickersByEmoji.clear();
        featuredStickerSetsById.clear();
        featuredStickerSets.clear();
        unreadStickerSets.clear();
        recentGifs.clear();
        stickerSetsById.clear();
        installedStickerSetsById.clear();
        stickerSetsByName.clear();
        loadingFeaturedStickers = false;
        featuredStickersLoaded = false;
        loadingRecentGifs = false;
        recentGifsLoaded = false;

        loading = false;
        loaded = false;
        hints.clear();
        inlineBots.clear();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadHints);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadInlineHints);

        drafts.clear();
        draftMessages.clear();
        preferences.edit().clear().commit();

        botInfos.clear();
        botKeyboards.clear();
        botKeyboardsByMids.clear();
    }

    public void checkStickers(int type) {
        if (!loadingStickers[type] && (!stickersLoaded[type] || Math.abs(System.currentTimeMillis() / 1000 - loadDate[type]) >= 60 * 60)) {
            loadStickers(type, true, false);
        }
    }

    public void checkFeaturedStickers() {
        if (!loadingFeaturedStickers && (!featuredStickersLoaded || Math.abs(System.currentTimeMillis() / 1000 - loadFeaturedDate) >= 60 * 60)) {
            loadFeaturedStickers(true, false);
        }
    }

    public ArrayList<TLRPC.Document> getRecentStickers(int type) {
        ArrayList<TLRPC.Document> arrayList = recentStickers[type];
        return new ArrayList<>(arrayList.subList(0, Math.min(arrayList.size(), 20)));
    }

    public ArrayList<TLRPC.Document> getRecentStickersNoCopy(int type) {
        return recentStickers[type];
    }

    public boolean isStickerInFavorites(TLRPC.Document document) {
        for (int a = 0; a < recentStickers[TYPE_FAVE].size(); a++) {
            TLRPC.Document d = recentStickers[TYPE_FAVE].get(a);
            if (d.id == document.id && d.dc_id == document.dc_id) {
                return true;
            }
        }
        return false;
    }

    public void addRecentSticker(final int type, TLRPC.Document document, int date, boolean remove) {
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
            maxCount = MessagesController.getInstance(currentAccount).maxFaveStickersCount;
        } else {
            maxCount = MessagesController.getInstance(currentAccount).maxRecentStickersCount;
        }
        if (recentStickers[type].size() > maxCount || remove) {
            final TLRPC.Document old = remove ? document : recentStickers[type].remove(recentStickers[type].size() - 1);
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
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
                        MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = " + cacheType).stepThis().dispose();
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
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recentDocumentsDidLoaded, false, type);
        }
    }

    public ArrayList<TLRPC.Document> getRecentGifs() {
        return new ArrayList<>(recentGifs);
    }

    public void removeRecentGif(final TLRPC.Document document) {
        recentGifs.remove(document);
        TLRPC.TL_messages_saveGif req = new TLRPC.TL_messages_saveGif();
        req.id = new TLRPC.TL_inputDocument();
        req.id.id = document.id;
        req.id.access_hash = document.access_hash;
        req.unsave = true;
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {

            }
        });
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + document.id + "' AND type = 2").stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void addRecentGif(TLRPC.Document document, int date) {
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
        if (recentGifs.size() > MessagesController.getInstance(currentAccount).maxRecentGifsCount) {
            final TLRPC.Document old = recentGifs.remove(recentGifs.size() - 1);
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM web_recent_v3 WHERE id = '" + old.id + "' AND type = 2").stepThis().dispose();
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

    public boolean isLoadingStickers(int type) {
        return loadingStickers[type];
    }

    public TLRPC.TL_messages_stickerSet getStickerSetByName(String name) {
        return stickerSetsByName.get(name);
    }

    public TLRPC.TL_messages_stickerSet getStickerSetById(long id) {
        return stickerSetsById.get(id);
    }

    public TLRPC.TL_messages_stickerSet getGroupStickerSetById(TLRPC.StickerSet stickerSet) {
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

    public void putGroupStickerSet(TLRPC.TL_messages_stickerSet stickerSet) {
        groupStickerSets.put(stickerSet.set.id, stickerSet);
    }

    private void loadGroupStickerSet(final TLRPC.StickerSet stickerSet, boolean cache) {
        if (cache) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        final TLRPC.TL_messages_stickerSet set;
                        SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT document FROM web_recent_v3 WHERE id = 's_" + stickerSet.id + "'");
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
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.groupStickersDidLoaded, set.set.id);
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (response != null) {
                        final TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) response;
                        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                            @Override
                            public void run() {
                                try {
                                    SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
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
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.groupStickersDidLoaded, set.set.id);
                            }
                        });
                    }
                }
            });
        }
    }

    public HashMap<String, ArrayList<TLRPC.Document>> getAllStickers() {
        return allStickers;
    }

    public HashMap<String, ArrayList<TLRPC.Document>> getAllStickersFeatured() {
        return allStickersFeatured;
    }

    public boolean canAddStickerToFavorites() {
        return !stickersLoaded[0] || stickerSets[0].size() >= 5 || !recentStickers[TYPE_FAVE].isEmpty();
    }

    public ArrayList<TLRPC.TL_messages_stickerSet> getStickerSets(int type) {
        if (type == TYPE_FEATURED) {
            return stickerSets[2];
        } else {
            return stickerSets[type];
        }
    }

    public ArrayList<TLRPC.StickerSetCovered> getFeaturedStickerSets() {
        return featuredStickerSets;
    }

    public ArrayList<Long> getUnreadStickerSets() {
        return unreadStickerSets;
    }

    public boolean isStickerPackInstalled(long id) {
        return installedStickerSetsById.indexOfKey(id) >= 0;
    }

    public boolean isStickerPackUnread(long id) {
        return unreadStickerSets.contains(id);
    }

    public boolean isStickerPackInstalled(String name) {
        return stickerSetsByName.containsKey(name);
    }

    public String getEmojiForSticker(long id) {
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

    public void loadRecents(final int type, final boolean gif, boolean cache, boolean force) {
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
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
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
                        SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT document FROM web_recent_v3 WHERE type = " + cacheType + " ORDER BY date DESC");
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
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recentDocumentsDidLoaded, gif, type);
                                loadRecents(type, gif, false, false);
                            }
                        });
                    } catch (Throwable e) {
                        FileLog.e(e);
                    }
                }
            });
        } else {
            SharedPreferences preferences = MessagesController.getEmojiSettings(currentAccount);
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
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
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
                ConnectionsManager.getInstance(currentAccount).sendRequest(request, new RequestDelegate() {
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

    private void processLoadedRecentDocuments(final int type, final ArrayList<TLRPC.Document> documents, final boolean gif, final int date) {
        if (documents != null) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                        int maxCount;
                        if (gif) {
                            maxCount = MessagesController.getInstance(currentAccount).maxRecentGifsCount;
                        } else {
                            if (type == TYPE_FAVE) {
                                maxCount = MessagesController.getInstance(currentAccount).maxFaveStickersCount;
                            } else {
                                maxCount = MessagesController.getInstance(currentAccount).maxRecentStickersCount;
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
                    SharedPreferences.Editor editor = MessagesController.getEmojiSettings(currentAccount).edit();
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
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.recentDocumentsDidLoaded, gif, type);
                    } else {

                    }
                }
            });
        }
    }

    public void reorderStickers(int type, final ArrayList<Long> order) {
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
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoaded, type);
        loadStickers(type, false, true);
    }

    public void calcNewHash(int type) {
        loadHash[type] = calcStickersHash(stickerSets[type]);
    }

    public void addNewStickerSet(final TLRPC.TL_messages_stickerSet set) {
        if (stickerSetsById.indexOfKey(set.set.id) >= 0 || stickerSetsByName.containsKey(set.set.short_name)) {
            return;
        }
        int type = set.set.masks ? TYPE_MASK : TYPE_IMAGE;
        stickerSets[type].add(0, set);
        stickerSetsById.put(set.set.id, set);
        installedStickerSetsById.put(set.set.id, set);
        stickerSetsByName.put(set.set.short_name, set);
        LongSparseArray<TLRPC.Document> stickersById = new LongSparseArray<>();
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
                if (stickersByEmoji.indexOfKey(id) < 0) {
                    stickersByEmoji.put(id, stickerPack.emoticon);
                }
                TLRPC.Document sticker = stickersById.get(id);
                if (sticker != null) {
                    arrayList.add(sticker);
                }
            }
        }
        loadHash[type] = calcStickersHash(stickerSets[type]);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoaded, type);
        loadStickers(type, false, true);
    }

    public void loadFeaturedStickers(boolean cache, boolean force) {
        if (loadingFeaturedStickers) {
            return;
        }
        loadingFeaturedStickers = true;
        if (cache) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    ArrayList<TLRPC.StickerSetCovered> newStickerArray = null;
                    ArrayList<Long> unread = new ArrayList<>();
                    int date = 0;
                    int hash = 0;
                    SQLiteCursor cursor = null;
                    try {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT data, unread, date, hash FROM stickers_featured WHERE 1");
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
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

    private void processLoadedFeaturedStickers(final ArrayList<TLRPC.StickerSetCovered> res, final ArrayList<Long> unreadStickers, final boolean cache, final int date, final int hash) {
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
                            loadFeaturedStickers(false, false);
                        }
                    }, res == null && !cache ? 1000 : 0);
                    if (res == null) {
                        return;
                    }
                }
                if (res != null) {
                    try {
                        final ArrayList<TLRPC.StickerSetCovered> stickerSetsNew = new ArrayList<>();
                        final LongSparseArray<TLRPC.StickerSetCovered> stickerSetsByIdNew = new LongSparseArray<>();

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
                                loadStickers(TYPE_FEATURED, true, false);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.featuredStickersDidLoaded);
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

    private void putFeaturedStickersToCache(ArrayList<TLRPC.StickerSetCovered> stickers, final ArrayList<Long> unreadStickers, final int date, final int hash) {
        final ArrayList<TLRPC.StickerSetCovered> stickersFinal = stickers != null ? new ArrayList<>(stickers) : null;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (stickersFinal != null) {
                        SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO stickers_featured VALUES(?, ?, ?, ?, ?)");
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
                        SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("UPDATE stickers_featured SET date = ?");
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

    private int calcFeaturedStickersHash(ArrayList<TLRPC.StickerSetCovered> sets) {
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

    public void markFaturedStickersAsRead(boolean query) {
        if (unreadStickerSets.isEmpty()) {
            return;
        }
        unreadStickerSets.clear();
        loadFeaturedHash = calcFeaturedStickersHash(featuredStickerSets);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.featuredStickersDidLoaded);
        putFeaturedStickersToCache(featuredStickerSets, unreadStickerSets, loadFeaturedDate, loadFeaturedHash);
        if (query) {
            TLRPC.TL_messages_readFeaturedStickers req = new TLRPC.TL_messages_readFeaturedStickers();
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
    }

    public int getFeaturesStickersHashWithoutUnread() {
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

    public void markFaturedStickersByIdAsRead(final long id) {
        if (!unreadStickerSets.contains(id) || readingStickerSets.contains(id)) {
            return;
        }
        readingStickerSets.add(id);
        TLRPC.TL_messages_readFeaturedStickers req = new TLRPC.TL_messages_readFeaturedStickers();
        req.id.add(id);
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
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
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.featuredStickersDidLoaded);
                putFeaturedStickersToCache(featuredStickerSets, unreadStickerSets, loadFeaturedDate, loadFeaturedHash);
            }
        }, 1000);
    }

    public int getArchivedStickersCount(int type) {
        return archivedStickersCount[type];
    }

    public void loadArchivedStickersCount(final int type, boolean cache) {
        if (cache) {
            SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
            int count = preferences.getInt("archivedStickersCount" + type, -1);
            if (count == -1) {
                loadArchivedStickersCount(type, false);
            } else {
                archivedStickersCount[type] = count;
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.archivedStickersCountDidLoaded, type);
            }
        } else {
            TLRPC.TL_messages_getArchivedStickers req = new TLRPC.TL_messages_getArchivedStickers();
            req.limit = 0;
            req.masks = type == TYPE_MASK;
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (error == null) {
                                TLRPC.TL_messages_archivedStickers res = (TLRPC.TL_messages_archivedStickers) response;
                                archivedStickersCount[type] = res.count;
                                SharedPreferences preferences = MessagesController.getNotificationsSettings(currentAccount);
                                preferences.edit().putInt("archivedStickersCount" + type, res.count).commit();
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.archivedStickersCountDidLoaded, type);
                            }
                        }
                    });
                }
            });
        }
    }

    private void processLoadStickersResponse(final int type, final TLRPC.TL_messages_allStickers res) {
        final ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = new ArrayList<>();
        if (res.sets.isEmpty()) {
            processLoadedStickers(type, newStickerArray, false, (int) (System.currentTimeMillis() / 1000), res.hash);
        } else {
            final LongSparseArray<TLRPC.TL_messages_stickerSet> newStickerSets = new LongSparseArray<>();
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

                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
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
    }

    public void loadStickers(final int type, boolean cache, boolean force) {
        if (loadingStickers[type]) {
            return;
        }
        if (type == TYPE_FEATURED) {
            if (featuredStickerSets.isEmpty() || !MessagesController.getInstance(currentAccount).preloadFeaturedStickers) {
                return;
            }
        } else {
            loadArchivedStickersCount(type, cache);
        }
        loadingStickers[type] = true;
        if (cache) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    ArrayList<TLRPC.TL_messages_stickerSet> newStickerArray = null;
                    int date = 0;
                    int hash = 0;
                    SQLiteCursor cursor = null;
                    try {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT data, date, hash FROM stickers_v2 WHERE id = " + (type + 1));
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
            if (type == TYPE_FEATURED) {
                TLRPC.TL_messages_allStickers response = new TLRPC.TL_messages_allStickers();
                response.hash = loadFeaturedHash;
                for (int a = 0, size = featuredStickerSets.size(); a < size; a++) {
                    response.sets.add(featuredStickerSets.get(a).set);
                }
                processLoadStickersResponse(type, response);
                return;
            }
            TLObject req;
            final int hash;
            if (type == TYPE_IMAGE) {
                req = new TLRPC.TL_messages_getAllStickers();
                hash = ((TLRPC.TL_messages_getAllStickers) req).hash = force ? 0 : loadHash[type];
            } else {
                req = new TLRPC.TL_messages_getMaskStickers();
                hash = ((TLRPC.TL_messages_getMaskStickers) req).hash = force ? 0 : loadHash[type];
            }
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response instanceof TLRPC.TL_messages_allStickers) {
                                processLoadStickersResponse(type, (TLRPC.TL_messages_allStickers) response);
                            } else {
                                processLoadedStickers(type, null, false, (int) (System.currentTimeMillis() / 1000), hash);
                            }
                        }
                    });
                }
            });
        }
    }

    private void putStickersToCache(final int type, ArrayList<TLRPC.TL_messages_stickerSet> stickers, final int date, final int hash) {
        final ArrayList<TLRPC.TL_messages_stickerSet> stickersFinal = stickers != null ? new ArrayList<>(stickers) : null;
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (stickersFinal != null) {
                        SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO stickers_v2 VALUES(?, ?, ?, ?)");
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
                        state.bindInteger(1, type + 1);
                        state.bindByteBuffer(2, data);
                        state.bindInteger(3, date);
                        state.bindInteger(4, hash);
                        state.step();
                        data.reuse();
                        state.dispose();
                    } else {
                        SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("UPDATE stickers_v2 SET date = ?");
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

    public String getStickerSetName(long setId) {
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

    private void processLoadedStickers(final int type, final ArrayList<TLRPC.TL_messages_stickerSet> res, final boolean cache, final int date, final int hash) {
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
                        final LongSparseArray<TLRPC.TL_messages_stickerSet> stickerSetsByIdNew = new LongSparseArray<>();
                        final HashMap<String, TLRPC.TL_messages_stickerSet> stickerSetsByNameNew = new HashMap<>();
                        final LongSparseArray<String> stickersByEmojiNew = new LongSparseArray<>();
                        final LongSparseArray<TLRPC.Document> stickersByIdNew = new LongSparseArray<>();
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
                                        if (stickersByEmojiNew.indexOfKey(id) < 0) {
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
                                    installedStickerSetsById.remove(set.id);
                                    stickerSetsByName.remove(set.short_name);
                                }
                                for (int a = 0; a < stickerSetsByIdNew.size(); a++) {
                                    stickerSetsById.put(stickerSetsByIdNew.keyAt(a), stickerSetsByIdNew.valueAt(a));
                                    if (type != TYPE_FEATURED) {
                                        installedStickerSetsById.put(stickerSetsByIdNew.keyAt(a), stickerSetsByIdNew.valueAt(a));
                                    }
                                }
                                stickerSetsByName.putAll(stickerSetsByNameNew);
                                stickerSets[type] = stickerSetsNew;
                                loadHash[type] = hash;
                                loadDate[type] = date;
                                if (type == TYPE_IMAGE) {
                                    allStickers = allStickersNew;
                                    stickersByEmoji = stickersByEmojiNew;
                                } else if (type == TYPE_FEATURED) {
                                    allStickersFeatured = allStickersNew;
                                }
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoaded, type);
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

    public void removeStickersSet(final Context context, final TLRPC.StickerSet stickerSet, final int hide, final BaseFragment baseFragment, final boolean showSettings) {
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
                        installedStickerSetsById.remove(set.set.id);
                        stickerSetsByName.remove(set.set.short_name);
                    }
                    break;
                }
            }
            loadHash[type] = calcStickersHash(stickerSets[type]);
            putStickersToCache(type, stickerSets[type], loadDate[type], loadHash[type]);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.stickersDidLoaded, type);
            TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
            req.stickerset = stickerSetID;
            req.archived = hide == 1;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (response instanceof TLRPC.TL_messages_stickerSetInstallResultArchive) {
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.needReloadArchivedStickers, type);
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
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
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
    //---------------- STICKERS END ----------------

    private int reqId;
    private int mergeReqId;
    private long lastMergeDialogId;
    private int lastReqId;
    private int messagesSearchCount[] = new int[] {0, 0};
    private boolean messagesSearchEndReached[] = new boolean[] {false, false};
    private ArrayList<MessageObject> searchResultMessages = new ArrayList<>();
    private SparseArray<MessageObject> searchResultMessagesMap[] = new SparseArray[] {new SparseArray<>(), new SparseArray<>()};
    private String lastSearchQuery;
    private int lastReturnedNum;

    private int getMask() {
        int mask = 0;
        if (lastReturnedNum < searchResultMessages.size() - 1 || !messagesSearchEndReached[0] || !messagesSearchEndReached[1]) {
            mask |= 1;
        }
        if (lastReturnedNum > 0) {
            mask |= 2;
        }
        return mask;
    }

    public boolean isMessageFound(final int messageId, boolean mergeDialog) {
        return searchResultMessagesMap[mergeDialog ? 1 : 0].indexOfKey(messageId) >= 0;
    }

    public void searchMessagesInChat(String query, final long dialog_id, final long mergeDialogId, final int guid, final int direction, TLRPC.User user) {
        searchMessagesInChat(query, dialog_id, mergeDialogId, guid, direction, false, user);
    }

    private void searchMessagesInChat(String query, final long dialog_id, final long mergeDialogId, final int guid, final int direction, final boolean internal, final TLRPC.User user) {
        int max_id = 0;
        long queryWithDialog = dialog_id;
        boolean firstQuery = !internal;
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (mergeReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(mergeReqId, true);
            mergeReqId = 0;
        }
        if (query == null) {
            if (searchResultMessages.isEmpty()) {
                return;
            }
            if (direction == 1) {
                lastReturnedNum++;
                if (lastReturnedNum < searchResultMessages.size()) {
                    MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                    return;
                } else {
                    if (messagesSearchEndReached[0] && mergeDialogId == 0 && messagesSearchEndReached[1]) {
                        lastReturnedNum--;
                        return;
                    }
                    firstQuery = false;
                    query = lastSearchQuery;
                    MessageObject messageObject = searchResultMessages.get(searchResultMessages.size() - 1);
                    if (messageObject.getDialogId() == dialog_id && !messagesSearchEndReached[0]) {
                        max_id = messageObject.getId();
                        queryWithDialog = dialog_id;
                    } else {
                        if (messageObject.getDialogId() == mergeDialogId) {
                            max_id = messageObject.getId();
                        }
                        queryWithDialog = mergeDialogId;
                        messagesSearchEndReached[1] = false;
                    }
                }
            } else if (direction == 2) {
                lastReturnedNum--;
                if (lastReturnedNum < 0) {
                    lastReturnedNum = 0;
                    return;
                }
                if (lastReturnedNum >= searchResultMessages.size()) {
                    lastReturnedNum = searchResultMessages.size() - 1;
                }
                MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                return;
            } else {
                return;
            }
        } else if (firstQuery) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatSearchResultsLoading, guid);
            messagesSearchEndReached[0] = messagesSearchEndReached[1] = false;
            messagesSearchCount[0] = messagesSearchCount[1] = 0;
            searchResultMessages.clear();
            searchResultMessagesMap[0].clear();
            searchResultMessagesMap[1].clear();
        }
        if (messagesSearchEndReached[0] && !messagesSearchEndReached[1] && mergeDialogId != 0) {
            queryWithDialog = mergeDialogId;
        }
        if (queryWithDialog == dialog_id && firstQuery) {
            if (mergeDialogId != 0) {
                TLRPC.InputPeer inputPeer = MessagesController.getInstance(currentAccount).getInputPeer((int) mergeDialogId);
                if (inputPeer == null) {
                    return;
                }
                final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
                req.peer = inputPeer;
                lastMergeDialogId = mergeDialogId;
                req.limit = 1;
                req.q = query != null ? query : "";
                if (user != null) {
                    req.from_id = MessagesController.getInstance(currentAccount).getInputUser(user);
                    req.flags |= 1;
                }
                req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
                mergeReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(final TLObject response, final TLRPC.TL_error error) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                if (lastMergeDialogId == mergeDialogId) {
                                    mergeReqId = 0;
                                    if (response != null) {
                                        TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                        messagesSearchEndReached[1] = res.messages.isEmpty();
                                        messagesSearchCount[1] = res instanceof TLRPC.TL_messages_messagesSlice ? res.count : res.messages.size();
                                        searchMessagesInChat(req.q, dialog_id, mergeDialogId, guid, direction, true, user);
                                    }
                                }
                            }
                        });
                    }
                }, ConnectionsManager.RequestFlagFailOnServerErrors);
                return;
            } else {
                lastMergeDialogId = 0;
                messagesSearchEndReached[1] = true;
                messagesSearchCount[1] = 0;
            }
        }
        final TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = MessagesController.getInstance(currentAccount).getInputPeer((int) queryWithDialog);
        if (req.peer == null) {
            return;
        }
        req.limit = 21;
        req.q = query != null ? query : "";
        req.offset_id = max_id;
        if (user != null) {
            req.from_id = MessagesController.getInstance(currentAccount).getInputUser(user);
            req.flags |= 1;
        }
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        final int currentReqId = ++lastReqId;
        lastSearchQuery = query;
        final long queryWithDialogFinal = queryWithDialog;
        reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(final TLObject response, final TLRPC.TL_error error) {
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        if (currentReqId == lastReqId) {
                            reqId = 0;
                            if (response != null) {
                                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                                for (int a = 0; a < res.messages.size(); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    if (message instanceof TLRPC.TL_messageEmpty || message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                                        res.messages.remove(a);
                                        a--;
                                    }
                                }
                                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                                MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                                if (req.offset_id == 0 && queryWithDialogFinal == dialog_id) {
                                    lastReturnedNum = 0;
                                    searchResultMessages.clear();
                                    searchResultMessagesMap[0].clear();
                                    searchResultMessagesMap[1].clear();
                                    messagesSearchCount[0] = 0;
                                }
                                boolean added = false;
                                for (int a = 0; a < Math.min(res.messages.size(), 20); a++) {
                                    TLRPC.Message message = res.messages.get(a);
                                    added = true;
                                    MessageObject messageObject = new MessageObject(currentAccount, message, false);
                                    searchResultMessages.add(messageObject);
                                    searchResultMessagesMap[queryWithDialogFinal == dialog_id ? 0 : 1].put(messageObject.getId(), messageObject);
                                }
                                messagesSearchEndReached[queryWithDialogFinal == dialog_id ? 0 : 1] = res.messages.size() != 21;
                                messagesSearchCount[queryWithDialogFinal == dialog_id ? 0 : 1] = res instanceof TLRPC.TL_messages_messagesSlice || res instanceof TLRPC.TL_messages_channelMessages ? res.count : res.messages.size();
                                if (searchResultMessages.isEmpty()) {
                                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, 0, getMask(), (long) 0, 0, 0);
                                } else {
                                    if (added) {
                                        if (lastReturnedNum >= searchResultMessages.size()) {
                                            lastReturnedNum = searchResultMessages.size() - 1;
                                        }
                                        MessageObject messageObject = searchResultMessages.get(lastReturnedNum);
                                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.chatSearchResultsAvailable, guid, messageObject.getId(), getMask(), messageObject.getDialogId(), lastReturnedNum, messagesSearchCount[0] + messagesSearchCount[1]);
                                    }
                                }
                                if (queryWithDialogFinal == dialog_id && messagesSearchEndReached[0] && mergeDialogId != 0 && !messagesSearchEndReached[1]) {
                                    searchMessagesInChat(lastSearchQuery, dialog_id, mergeDialogId, guid, 0, true, user);
                                }
                            }
                        }
                    }
                });
            }
        }, ConnectionsManager.RequestFlagFailOnServerErrors);
    }

    public String getLastSearchQuery() {
        return lastSearchQuery;
    }
    //---------------- MESSAGE SEARCH END ----------------


    public final static int MEDIA_PHOTOVIDEO = 0;
    public final static int MEDIA_FILE = 1;
    public final static int MEDIA_AUDIO = 2;
    public final static int MEDIA_URL = 3;
    public final static int MEDIA_MUSIC = 4;
    public final static int MEDIA_TYPES_COUNT = 5;

    public void loadMedia(final long uid, final int count, final int max_id, final int type, final boolean fromCache, final int classGuid) {
        final boolean isChannel = (int) uid < 0 && ChatObject.isChannel(-(int) uid, currentAccount);

        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            loadMediaDatabase(uid, count, max_id, type, classGuid, isChannel);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.limit = count + 1;
            req.offset_id = max_id;
            if (type == MEDIA_PHOTOVIDEO) {
                req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            } else if (type == MEDIA_FILE) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (type == MEDIA_AUDIO) {
                req.filter = new TLRPC.TL_inputMessagesFilterRoundVoice();
            } else if (type == MEDIA_URL) {
                req.filter = new TLRPC.TL_inputMessagesFilterUrl();
            } else if (type == MEDIA_MUSIC) {
                req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            }
            req.q = "";
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(lower_part);
            if (req.peer == null) {
                return;
            }
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        boolean topReached;
                        if (res.messages.size() > count) {
                            topReached = false;
                            res.messages.remove(res.messages.size() - 1);
                        } else {
                            topReached = true;
                        }
                        processLoadedMedia(res, uid, count, max_id, type, false, classGuid, isChannel, topReached);
                    }
                }
            });
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }
    }

    public void getMediaCount(final long uid, final int type, final int classGuid, boolean fromCache) {
        int lower_part = (int)uid;
        if (fromCache || lower_part == 0) {
            getMediaCountDatabase(uid, type, classGuid);
        } else {
            TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
            req.limit = 1;
            req.offset_id = 0;
            if (type == MEDIA_PHOTOVIDEO) {
                req.filter = new TLRPC.TL_inputMessagesFilterPhotoVideo();
            } else if (type == MEDIA_FILE) {
                req.filter = new TLRPC.TL_inputMessagesFilterDocument();
            } else if (type == MEDIA_AUDIO) {
                req.filter = new TLRPC.TL_inputMessagesFilterRoundVoice();
            } else if (type == MEDIA_URL) {
                req.filter = new TLRPC.TL_inputMessagesFilterUrl();
            } else if (type == MEDIA_MUSIC) {
                req.filter = new TLRPC.TL_inputMessagesFilterMusic();
            }
            req.q = "";
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(lower_part);
            if (req.peer == null) {
                return;
            }
            int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {
                    if (error == null) {
                        final TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                        MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                        int count;
                        if (res instanceof TLRPC.TL_messages_messages) {
                            count = res.messages.size();
                        } else {
                            count = res.count;
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                MessagesController.getInstance(currentAccount).putUsers(res.users, false);
                                MessagesController.getInstance(currentAccount).putChats(res.chats, false);
                            }
                        });

                        processLoadedMediaCount(count, uid, type, classGuid, false);
                    }
                }
            });
            ConnectionsManager.getInstance(currentAccount).bindRequestToGuid(reqId, classGuid);
        }
    }

    public static int getMediaType(TLRPC.Message message) {
        if (message == null) {
            return -1;
        }
        if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
            return MEDIA_PHOTOVIDEO;
        } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
            if (MessageObject.isVoiceMessage(message) || MessageObject.isRoundVideoMessage(message)) {
                return MEDIA_AUDIO;
            } else if (MessageObject.isVideoMessage(message)) {
                return MEDIA_PHOTOVIDEO;
            } else if (MessageObject.isStickerMessage(message)) {
                return -1;
            } else if (MessageObject.isMusicMessage(message)) {
                return MEDIA_MUSIC;
            } else {
                return MEDIA_FILE;
            }
        } else if (!message.entities.isEmpty()) {
            for (int a = 0; a < message.entities.size(); a++) {
                TLRPC.MessageEntity entity = message.entities.get(a);
                if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityTextUrl || entity instanceof TLRPC.TL_messageEntityEmail) {
                    return MEDIA_URL;
                }
            }
        }
        return -1;
    }

    public static boolean canAddMessageToMedia(TLRPC.Message message) {
        if (message instanceof TLRPC.TL_message_secret && (message.media instanceof TLRPC.TL_messageMediaPhoto || MessageObject.isVideoMessage(message) || MessageObject.isGifMessage(message)) && message.media.ttl_seconds != 0 && message.media.ttl_seconds <= 60) {
            return false;
        } else if (!(message instanceof TLRPC.TL_message_secret) && message instanceof TLRPC.TL_message && (message.media instanceof TLRPC.TL_messageMediaPhoto || message.media instanceof TLRPC.TL_messageMediaDocument) && message.media.ttl_seconds != 0) {
            return false;
        } else if (message.media instanceof TLRPC.TL_messageMediaPhoto ||
                message.media instanceof TLRPC.TL_messageMediaDocument && !MessageObject.isGifDocument(message.media.document)) {
            return true;
        } else if (!message.entities.isEmpty()) {
            for (int a = 0; a < message.entities.size(); a++) {
                TLRPC.MessageEntity entity = message.entities.get(a);
                if (entity instanceof TLRPC.TL_messageEntityUrl || entity instanceof TLRPC.TL_messageEntityTextUrl || entity instanceof TLRPC.TL_messageEntityEmail) {
                    return true;
                }
            }
        }
        return false;
    }

    private void processLoadedMedia(final TLRPC.messages_Messages res, final long uid, int count, int max_id, final int type, final boolean fromCache, final int classGuid, final boolean isChannel, final boolean topReached) {
        int lower_part = (int)uid;
        if (fromCache && res.messages.isEmpty() && lower_part != 0) {
            loadMedia(uid, count, max_id, type, false, classGuid);
        } else {
            if (!fromCache) {
                ImageLoader.saveMessagesThumbs(res.messages);
                MessagesStorage.getInstance(currentAccount).putUsersAndChats(res.users, res.chats, true, true);
                putMediaDatabase(uid, type, res.messages, max_id, topReached);
            }

            final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
            for (int a = 0; a < res.users.size(); a++) {
                TLRPC.User u = res.users.get(a);
                usersDict.put(u.id, u);
            }
            final ArrayList<MessageObject> objects = new ArrayList<>();
            for (int a = 0; a < res.messages.size(); a++) {
                TLRPC.Message message = res.messages.get(a);
                objects.add(new MessageObject(currentAccount, message, usersDict, true));
            }

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    int totalCount = res.count;
                    MessagesController.getInstance(currentAccount).putUsers(res.users, fromCache);
                    MessagesController.getInstance(currentAccount).putChats(res.chats, fromCache);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mediaDidLoaded, uid, totalCount, objects, classGuid, type, topReached);
                }
            });
        }
    }

    private void processLoadedMediaCount(final int count, final long uid, final int type, final int classGuid, final boolean fromCache) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                int lower_part = (int) uid;
                if (fromCache && (count == -1 || count == 0 && type == 2) && lower_part != 0) {
                    getMediaCount(uid, type, classGuid, false);
                } else {
                    if (!fromCache) {
                        putMediaCountDatabase(uid, type, count);
                    }
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.mediaCountDidLoaded, uid, (fromCache && count == -1 ? 0 : count), fromCache, type);
                }
            }
        });
    }

    private void putMediaCountDatabase(final long uid, final int type, final int count) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state2 = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO media_counts_v2 VALUES(?, ?, ?)");
                    state2.requery();
                    state2.bindLong(1, uid);
                    state2.bindInteger(2, type);
                    state2.bindInteger(3, count);
                    state2.step();
                    state2.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void getMediaCountDatabase(final long uid, final int type, final int classGuid) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    int count = -1;
                    SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT count FROM media_counts_v2 WHERE uid = %d AND type = %d LIMIT 1", uid, type));
                    if (cursor.next()) {
                        count = cursor.intValue(0);
                    }
                    cursor.dispose();
                    int lower_part = (int)uid;
                    if (count == -1 && lower_part == 0) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT COUNT(mid) FROM media_v2 WHERE uid = %d AND type = %d LIMIT 1", uid, type));
                        if (cursor.next()) {
                            count = cursor.intValue(0);
                        }
                        cursor.dispose();

                        if (count != -1) {
                            putMediaCountDatabase(uid, type, count);
                        }
                    }
                    processLoadedMediaCount(count, uid, type, classGuid, true);
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void loadMediaDatabase(final long uid, final int count, final int max_id, final int type, final int classGuid, final boolean isChannel) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                boolean topReached = false;
                TLRPC.TL_messages_messages res = new TLRPC.TL_messages_messages();
                try {
                    ArrayList<Integer> usersToLoad = new ArrayList<>();
                    ArrayList<Integer> chatsToLoad = new ArrayList<>();
                    int countToLoad = count + 1;

                    SQLiteCursor cursor;
                    SQLiteDatabase database = MessagesStorage.getInstance(currentAccount).getDatabase();
                    boolean isEnd = false;
                    if ((int) uid != 0) {
                        int channelId = 0;
                        long messageMaxId = max_id;
                        if (isChannel) {
                            channelId = -(int) uid;
                        }
                        if (messageMaxId != 0 && channelId != 0) {
                            messageMaxId |= ((long) channelId) << 32;
                        }

                        cursor = database.queryFinalized(String.format(Locale.US, "SELECT start FROM media_holes_v2 WHERE uid = %d AND type = %d AND start IN (0, 1)", uid, type));
                        if (cursor.next()) {
                            isEnd = cursor.intValue(0) == 1;
                            cursor.dispose();
                        } else {
                            cursor.dispose();
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT min(mid) FROM media_v2 WHERE uid = %d AND type = %d AND mid > 0", uid, type));
                            if (cursor.next()) {
                                int mid = cursor.intValue(0);
                                if (mid != 0) {
                                    SQLitePreparedStatement state = database.executeFast("REPLACE INTO media_holes_v2 VALUES(?, ?, ?, ?)");
                                    state.requery();
                                    state.bindLong(1, uid);
                                    state.bindInteger(2, type);
                                    state.bindInteger(3, 0);
                                    state.bindInteger(4, mid);
                                    state.step();
                                    state.dispose();
                                }
                            }
                            cursor.dispose();
                        }

                        if (messageMaxId != 0) {
                            long holeMessageId = 0;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT end FROM media_holes_v2 WHERE uid = %d AND type = %d AND end <= %d ORDER BY end DESC LIMIT 1", uid, type, max_id));
                            if (cursor.next()) {
                                holeMessageId = cursor.intValue(0);
                                if (channelId != 0) {
                                    holeMessageId |= ((long) channelId) << 32;
                                }
                            }
                            cursor.dispose();
                            if (holeMessageId > 1) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND mid > 0 AND mid < %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, messageMaxId, holeMessageId, type, countToLoad));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND mid > 0 AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, messageMaxId, type, countToLoad));
                            }
                        } else {
                            long holeMessageId = 0;
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT max(end) FROM media_holes_v2 WHERE uid = %d AND type = %d", uid, type));
                            if (cursor.next()) {
                                holeMessageId = cursor.intValue(0);
                                if (channelId != 0) {
                                    holeMessageId |= ((long) channelId) << 32;
                                }
                            }
                            cursor.dispose();
                            if (holeMessageId > 1) {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND mid >= %d AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, holeMessageId, type, countToLoad));
                            } else {
                                cursor = database.queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND mid > 0 AND type = %d ORDER BY date DESC, mid DESC LIMIT %d", uid, type, countToLoad));
                            }
                        }
                    } else {
                        isEnd = true;
                        if (max_id != 0) {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v2 as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND m.mid > %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, max_id, type, countToLoad));
                        } else {
                            cursor = database.queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, r.random_id FROM media_v2 as m LEFT JOIN randoms as r ON r.mid = m.mid WHERE m.uid = %d AND type = %d ORDER BY m.mid ASC LIMIT %d", uid, type, countToLoad));
                        }
                    }

                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                            data.reuse();
                            message.id = cursor.intValue(1);
                            message.dialog_id = uid;
                            if ((int) uid == 0) {
                                message.random_id = cursor.longValue(2);
                            }
                            res.messages.add(message);
                            MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                        }
                    }
                    cursor.dispose();

                    if (!usersToLoad.isEmpty()) {
                        MessagesStorage.getInstance(currentAccount).getUsersInternal(TextUtils.join(",", usersToLoad), res.users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        MessagesStorage.getInstance(currentAccount).getChatsInternal(TextUtils.join(",", chatsToLoad), res.chats);
                    }
                    if (res.messages.size() > count) {
                        topReached = false;
                        res.messages.remove(res.messages.size() - 1);
                    } else {
                        topReached = isEnd;
                    }
                } catch (Exception e) {
                    res.messages.clear();
                    res.chats.clear();
                    res.users.clear();
                    FileLog.e(e);
                } finally {
                    processLoadedMedia(res, uid, count, max_id, type, true, classGuid, isChannel, topReached);
                }
            }
        });
    }

    private void putMediaDatabase(final long uid, final int type, final ArrayList<TLRPC.Message> messages, final int max_id, final boolean topReached) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    if (messages.isEmpty() || topReached) {
                        MessagesStorage.getInstance(currentAccount).doneHolesInMedia(uid, max_id, type);
                        if (messages.isEmpty()) {
                            return;
                        }
                    }
                    MessagesStorage.getInstance(currentAccount).getDatabase().beginTransaction();
                    SQLitePreparedStatement state2 = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO media_v2 VALUES(?, ?, ?, ?, ?)");
                    for (TLRPC.Message message : messages) {
                        if (canAddMessageToMedia(message)) {

                            long messageId = message.id;
                            if (message.to_id.channel_id != 0) {
                                messageId |= ((long) message.to_id.channel_id) << 32;
                            }

                            state2.requery();
                            NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                            message.serializeToStream(data);
                            state2.bindLong(1, messageId);
                            state2.bindLong(2, uid);
                            state2.bindInteger(3, message.date);
                            state2.bindInteger(4, type);
                            state2.bindByteBuffer(5, data);
                            state2.step();
                            data.reuse();
                        }
                    }
                    state2.dispose();
                    if (!topReached || max_id != 0) {
                        int minId = topReached ? 1 : messages.get(messages.size() - 1).id;
                        if (max_id != 0) {
                            MessagesStorage.getInstance(currentAccount).closeHolesInMedia(uid, minId, max_id, type);
                        } else {
                            MessagesStorage.getInstance(currentAccount).closeHolesInMedia(uid, minId, Integer.MAX_VALUE, type);
                        }
                    }
                    MessagesStorage.getInstance(currentAccount).getDatabase().commitTransaction();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void loadMusic(final long uid, final long max_id) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                final ArrayList<MessageObject> arrayList = new ArrayList<>();
                try {
                    int lower_id = (int) uid;
                    SQLiteCursor cursor;
                    if (lower_id != 0) {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND mid < %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", uid, max_id, MEDIA_MUSIC));
                    } else {
                        cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid FROM media_v2 WHERE uid = %d AND mid > %d AND type = %d ORDER BY date DESC, mid DESC LIMIT 1000", uid, max_id, MEDIA_MUSIC));
                    }

                    while (cursor.next()) {
                        NativeByteBuffer data = cursor.byteBufferValue(0);
                        if (data != null) {
                            TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                            message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                            data.reuse();
                            if (MessageObject.isMusicMessage(message)) {
                                message.id = cursor.intValue(1);
                                message.dialog_id = uid;
                                arrayList.add(0, new MessageObject(currentAccount, message, false));
                            }
                        }
                    }
                    cursor.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.musicDidLoaded, uid, arrayList);
                    }
                });
            }
        });
    }
    //---------------- MEDIA END ----------------

    public ArrayList<TLRPC.TL_topPeer> hints = new ArrayList<>();
    public ArrayList<TLRPC.TL_topPeer> inlineBots = new ArrayList<>();
    boolean loaded;
    boolean loading;

    private static Paint roundPaint, erasePaint;
    private static RectF bitmapRect;
    private static Path roundPath;

    public void buildShortcuts() {
        if (Build.VERSION.SDK_INT < 25) {
            return;
        }
        final ArrayList<TLRPC.TL_topPeer> hintsFinal = new ArrayList<>();
        for (int a = 0; a < hints.size(); a++) {
            hintsFinal.add(hints.get(a));
            if (hintsFinal.size() == 3) {
                break;
            }
        }
        Utilities.globalQueue.postRunnable(new Runnable() {
            @SuppressLint("NewApi")
            @Override
            public void run() {
                try {
                    ShortcutManager shortcutManager = ApplicationLoader.applicationContext.getSystemService(ShortcutManager.class);
                    List<ShortcutInfo> currentShortcuts = shortcutManager.getDynamicShortcuts();
                    ArrayList<String> shortcutsToUpdate = new ArrayList<>();
                    ArrayList<String> newShortcutsIds = new ArrayList<>();
                    ArrayList<String> shortcutsToDelete = new ArrayList<>();

                    if (currentShortcuts != null && !currentShortcuts.isEmpty()) {
                        newShortcutsIds.add("compose");
                        for (int a = 0; a < hintsFinal.size(); a++) {
                            TLRPC.TL_topPeer hint = hintsFinal.get(a);
                            long did;
                            if (hint.peer.user_id != 0) {
                                did = hint.peer.user_id;
                            } else {
                                did = -hint.peer.chat_id;
                                if (did == 0) {
                                    did = -hint.peer.channel_id;
                                }
                            }
                            newShortcutsIds.add("did" + did);
                        }
                        for (int a = 0; a < currentShortcuts.size(); a++) {
                            String id = currentShortcuts.get(a).getId();
                            if (!newShortcutsIds.remove(id)) {
                                shortcutsToDelete.add(id);
                            }
                            shortcutsToUpdate.add(id);
                        }
                        if (newShortcutsIds.isEmpty() && shortcutsToDelete.isEmpty()) {
                            return;
                        }
                    }

                    Intent intent = new Intent(ApplicationLoader.applicationContext, LaunchActivity.class);
                    intent.setAction("new_dialog");
                    ArrayList<ShortcutInfo> arrayList = new ArrayList<>();
                    arrayList.add(new ShortcutInfo.Builder(ApplicationLoader.applicationContext, "compose")
                            .setShortLabel(LocaleController.getString("NewConversationShortcut", R.string.NewConversationShortcut))
                            .setLongLabel(LocaleController.getString("NewConversationShortcut", R.string.NewConversationShortcut))
                            .setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_compose))
                            .setIntent(intent)
                            .build());
                    if (shortcutsToUpdate.contains("compose")) {
                        shortcutManager.updateShortcuts(arrayList);
                    } else {
                        shortcutManager.addDynamicShortcuts(arrayList);
                    }
                    arrayList.clear();

                    if (!shortcutsToDelete.isEmpty()) {
                        shortcutManager.removeDynamicShortcuts(shortcutsToDelete);
                    }

                    for (int a = 0; a < hintsFinal.size(); a++) {
                        Intent shortcutIntent = new Intent(ApplicationLoader.applicationContext, OpenChatReceiver.class);
                        TLRPC.TL_topPeer hint = hintsFinal.get(a);

                        TLRPC.User user = null;
                        TLRPC.Chat chat = null;
                        long did;
                        if (hint.peer.user_id != 0) {
                            shortcutIntent.putExtra("userId", hint.peer.user_id);
                            user = MessagesController.getInstance(currentAccount).getUser(hint.peer.user_id);
                            did = hint.peer.user_id;
                        } else {
                            int chat_id = hint.peer.chat_id;
                            if (chat_id == 0) {
                                chat_id = hint.peer.channel_id;
                            }
                            chat = MessagesController.getInstance(currentAccount).getChat(chat_id);
                            shortcutIntent.putExtra("chatId", chat_id);
                            did = -chat_id;
                        }
                        if (user == null && chat == null) {
                            continue;
                        }

                        String name;
                        TLRPC.FileLocation photo = null;

                        if (user != null) {
                            name = ContactsController.formatName(user.first_name, user.last_name);
                            if (user.photo != null) {
                                photo = user.photo.photo_small;
                            }
                        } else {
                            name = chat.title;
                            if (chat.photo != null) {
                                photo = chat.photo.photo_small;
                            }
                        }

                        shortcutIntent.putExtra("currentAccount", currentAccount);
                        shortcutIntent.setAction("com.tmessages.openchat" + did);
                        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

                        Bitmap bitmap = null;
                        if (photo != null) {
                            try {
                                File path = FileLoader.getPathToAttach(photo, true);
                                bitmap = BitmapFactory.decodeFile(path.toString());
                                if (bitmap != null) {
                                    int size = AndroidUtilities.dp(48);
                                    Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                                    Canvas canvas = new Canvas(result);
                                    if (roundPaint == null) {
                                        roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                                        bitmapRect = new RectF();
                                        erasePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                        erasePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                                        roundPath = new Path();
                                        roundPath.addCircle(size / 2, size / 2, size / 2 - AndroidUtilities.dp(2), Path.Direction.CW);
                                        roundPath.toggleInverseFillType();
                                    }
                                    bitmapRect.set(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(46), AndroidUtilities.dp(46));
                                    canvas.drawBitmap(bitmap, null, bitmapRect, roundPaint);
                                    canvas.drawPath(roundPath, erasePaint);
                                    try {
                                        canvas.setBitmap(null);
                                    } catch (Exception ignore) {

                                    }
                                    bitmap = result;
                                }
                            } catch (Throwable e) {
                                FileLog.e(e);
                            }
                        }

                        String id = "did" + did;
                        if (TextUtils.isEmpty(name)) {
                            name = " ";
                        }
                        ShortcutInfo.Builder builder = new ShortcutInfo.Builder(ApplicationLoader.applicationContext, id)
                                .setShortLabel(name)
                                .setLongLabel(name)
                                .setIntent(shortcutIntent);
                        if (bitmap != null) {
                            builder.setIcon(Icon.createWithBitmap(bitmap));
                        } else {
                            builder.setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.shortcut_user));
                        }
                        arrayList.add(builder.build());
                        if (shortcutsToUpdate.contains(id)) {
                            shortcutManager.updateShortcuts(arrayList);
                        } else {
                            shortcutManager.addDynamicShortcuts(arrayList);
                        }
                        arrayList.clear();
                    }
                } catch (Throwable ignore) {

                }
            }
        });
    }

    public void loadHints(boolean cache) {
        if (loading || !UserConfig.getInstance(currentAccount).suggestContacts) {
            return;
        }
        if (cache) {
            if (loaded) {
                return;
            }
            loading = true;
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    final ArrayList<TLRPC.TL_topPeer> hintsNew = new ArrayList<>();
                    final ArrayList<TLRPC.TL_topPeer> inlineBotsNew = new ArrayList<>();
                    final ArrayList<TLRPC.User> users = new ArrayList<>();
                    final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                    int selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                    try {
                        ArrayList<Integer> usersToLoad = new ArrayList<>();
                        ArrayList<Integer> chatsToLoad = new ArrayList<>();
                        SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized("SELECT did, type, rating FROM chat_hints WHERE 1 ORDER BY rating DESC");
                        while (cursor.next()) {
                            int did = cursor.intValue(0);
                            if (did == selfUserId) {
                                continue;
                            }
                            int type = cursor.intValue(1);
                            TLRPC.TL_topPeer peer = new TLRPC.TL_topPeer();
                            peer.rating = cursor.doubleValue(2);
                            if (did > 0) {
                                peer.peer = new TLRPC.TL_peerUser();
                                peer.peer.user_id = did;
                                usersToLoad.add(did);
                            } else {
                                peer.peer = new TLRPC.TL_peerChat();
                                peer.peer.chat_id = -did;
                                chatsToLoad.add(-did);
                            }
                            if (type == 0) {
                                hintsNew.add(peer);
                            } else if (type == 1) {
                                inlineBotsNew.add(peer);
                            }
                        }
                        cursor.dispose();
                        if (!usersToLoad.isEmpty()) {
                            MessagesStorage.getInstance(currentAccount).getUsersInternal(TextUtils.join(",", usersToLoad), users);
                        }

                        if (!chatsToLoad.isEmpty()) {
                            MessagesStorage.getInstance(currentAccount).getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                MessagesController.getInstance(currentAccount).putUsers(users, true);
                                MessagesController.getInstance(currentAccount).putChats(chats, true);
                                loading = false;
                                loaded = true;
                                hints = hintsNew;
                                inlineBots = inlineBotsNew;
                                buildShortcuts();
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadHints);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadInlineHints);
                                if (Math.abs(UserConfig.getInstance(currentAccount).lastHintsSyncTime - (int) (System.currentTimeMillis() / 1000)) >= 24 * 60 * 60) {
                                    loadHints(false);
                                }
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
            loaded = true;
        } else {
            loading = true;
            TLRPC.TL_contacts_getTopPeers req = new TLRPC.TL_contacts_getTopPeers();
            req.hash = 0;
            req.bots_pm = false;
            req.correspondents = true;
            req.groups = false;
            req.channels = false;
            req.bots_inline = true;
            req.offset = 0;
            req.limit = 20;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(final TLObject response, TLRPC.TL_error error) {
                    if (response instanceof TLRPC.TL_contacts_topPeers) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                final TLRPC.TL_contacts_topPeers topPeers = (TLRPC.TL_contacts_topPeers) response;
                                MessagesController.getInstance(currentAccount).putUsers(topPeers.users, false);
                                MessagesController.getInstance(currentAccount).putChats(topPeers.chats, false);
                                for (int a = 0; a < topPeers.categories.size(); a++) {
                                    TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                                    if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                        inlineBots = category.peers;
                                        UserConfig.getInstance(currentAccount).botRatingLoadTime = (int) (System.currentTimeMillis() / 1000);
                                    } else {
                                        hints = category.peers;
                                        int selfUserId = UserConfig.getInstance(currentAccount).getClientUserId();
                                        for (int b = 0; b < hints.size(); b++) {
                                            TLRPC.TL_topPeer topPeer = hints.get(b);
                                            if (topPeer.peer.user_id == selfUserId) {
                                                hints.remove(b);
                                                break;
                                            }
                                        }
                                        UserConfig.getInstance(currentAccount).ratingLoadTime = (int) (System.currentTimeMillis() / 1000);
                                    }
                                }
                                UserConfig.getInstance(currentAccount).saveConfig(false);
                                buildShortcuts();
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadHints);
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadInlineHints);
                                MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                                    @Override
                                    public void run() {
                                        try {
                                            MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose();
                                            MessagesStorage.getInstance(currentAccount).getDatabase().beginTransaction();
                                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(topPeers.users, topPeers.chats, false, false);

                                            SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)");
                                            for (int a = 0; a < topPeers.categories.size(); a++) {
                                                int type;
                                                TLRPC.TL_topPeerCategoryPeers category = topPeers.categories.get(a);
                                                if (category.category instanceof TLRPC.TL_topPeerCategoryBotsInline) {
                                                    type = 1;
                                                } else {
                                                    type = 0;
                                                }
                                                for (int b = 0; b < category.peers.size(); b++) {
                                                    TLRPC.TL_topPeer peer = category.peers.get(b);
                                                    int did;
                                                    if (peer.peer instanceof TLRPC.TL_peerUser) {
                                                        did = peer.peer.user_id;
                                                    } else if (peer.peer instanceof TLRPC.TL_peerChat) {
                                                        did = -peer.peer.chat_id;
                                                    } else {
                                                        did = -peer.peer.channel_id;
                                                    }
                                                    state.requery();
                                                    state.bindInteger(1, did);
                                                    state.bindInteger(2, type);
                                                    state.bindDouble(3, peer.rating);
                                                    state.bindInteger(4, 0);
                                                    state.step();
                                                }
                                            }

                                            state.dispose();

                                            MessagesStorage.getInstance(currentAccount).getDatabase().commitTransaction();
                                            AndroidUtilities.runOnUIThread(new Runnable() {
                                                @Override
                                                public void run() {
                                                    UserConfig.getInstance(currentAccount).suggestContacts = true;
                                                    UserConfig.getInstance(currentAccount).lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000);
                                                    UserConfig.getInstance(currentAccount).saveConfig(false);
                                                }
                                            });
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                    }
                                });
                            }
                        });
                    } else if (response instanceof TLRPC.TL_contacts_topPeersDisabled) {
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                UserConfig.getInstance(currentAccount).suggestContacts = false;
                                UserConfig.getInstance(currentAccount).lastHintsSyncTime = (int) (System.currentTimeMillis() / 1000);
                                UserConfig.getInstance(currentAccount).saveConfig(false);
                                clearTopPeers();
                            }
                        });
                    }
                }
            });
        }
    }

    public void clearTopPeers() {
        hints.clear();
        inlineBots.clear();
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadHints);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadInlineHints);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("DELETE FROM chat_hints WHERE 1").stepThis().dispose();
                } catch (Exception ignore) {

                }
            }
        });
        buildShortcuts();
    }

    public void increaseInlineRaiting(final int uid) {
        if (!UserConfig.getInstance(currentAccount).suggestContacts) {
            return;
        }
        int dt;
        if (UserConfig.getInstance(currentAccount).botRatingLoadTime != 0) {
            dt = Math.max(1, ((int) (System.currentTimeMillis() / 1000)) - UserConfig.getInstance(currentAccount).botRatingLoadTime);
        } else {
            dt = 60;
        }

        TLRPC.TL_topPeer peer = null;
        for (int a = 0; a < inlineBots.size(); a++) {
            TLRPC.TL_topPeer p = inlineBots.get(a);
            if (p.peer.user_id == uid) {
                peer = p;
                break;
            }
        }
        if (peer == null) {
            peer = new TLRPC.TL_topPeer();
            peer.peer = new TLRPC.TL_peerUser();
            peer.peer.user_id = uid;
            inlineBots.add(peer);
        }
        peer.rating += Math.exp(dt / MessagesController.getInstance(currentAccount).ratingDecay);
        Collections.sort(inlineBots, new Comparator<TLRPC.TL_topPeer>() {
            @Override
            public int compare(TLRPC.TL_topPeer lhs, TLRPC.TL_topPeer rhs) {
                if (lhs.rating > rhs.rating) {
                    return -1;
                } else if (lhs.rating < rhs.rating) {
                    return 1;
                }
                return 0;
            }
        });
        if (inlineBots.size() > 20) {
            inlineBots.remove(inlineBots.size() - 1);
        }
        savePeer(uid, 1, peer.rating);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadInlineHints);
    }

    public void removeInline(final int uid) {
        TLRPC.TL_topPeerCategoryPeers category = null;
        for (int a = 0; a < inlineBots.size(); a++) {
            if (inlineBots.get(a).peer.user_id == uid) {
                inlineBots.remove(a);
                TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryBotsInline();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(uid);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                });
                deletePeer(uid, 1);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadInlineHints);
                return;
            }
        }
    }

    public void removePeer(final int uid) {
        for (int a = 0; a < hints.size(); a++) {
            if (hints.get(a).peer.user_id == uid) {
                hints.remove(a);
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadHints);
                TLRPC.TL_contacts_resetTopPeerRating req = new TLRPC.TL_contacts_resetTopPeerRating();
                req.category = new TLRPC.TL_topPeerCategoryCorrespondents();
                req.peer = MessagesController.getInstance(currentAccount).getInputPeer(uid);
                deletePeer(uid, 0);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {

                    }
                });
                return;
            }
        }
    }

    public void increasePeerRaiting(final long did) {
        if (!UserConfig.getInstance(currentAccount).suggestContacts) {
            return;
        }
        final int lower_id = (int) did;
        if (lower_id <= 0) {
            return;
        }
        //remove chats and bots for now
        final TLRPC.User user = lower_id > 0 ? MessagesController.getInstance(currentAccount).getUser(lower_id) : null;
        //final TLRPC.Chat chat = lower_id < 0 ? MessagesController.getInstance().getChat(-lower_id) : null;
        if (user == null || user.bot/*&& chat == null || ChatObject.isChannel(chat) && !chat.megagroup*/) {
            return;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                double dt = 0;
                try {
                    int lastTime = 0;
                    int lastMid = 0;
                    SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT MAX(mid), MAX(date) FROM messages WHERE uid = %d AND out = 1", did));
                    if (cursor.next()) {
                        lastMid = cursor.intValue(0);
                        lastTime = cursor.intValue(1);
                    }
                    cursor.dispose();
                    if (lastMid > 0 && UserConfig.getInstance(currentAccount).ratingLoadTime != 0) {
                        dt = (lastTime - UserConfig.getInstance(currentAccount).ratingLoadTime);
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
                final double dtFinal = dt;
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        TLRPC.TL_topPeer peer = null;
                        for (int a = 0; a < hints.size(); a++) {
                            TLRPC.TL_topPeer p = hints.get(a);
                            if (lower_id < 0 && (p.peer.chat_id == -lower_id || p.peer.channel_id == -lower_id) || lower_id > 0 && p.peer.user_id == lower_id) {
                                peer = p;
                                break;
                            }
                        }
                        if (peer == null) {
                            peer = new TLRPC.TL_topPeer();
                            if (lower_id > 0) {
                                peer.peer = new TLRPC.TL_peerUser();
                                peer.peer.user_id = lower_id;
                            } else {
                                peer.peer = new TLRPC.TL_peerChat();
                                peer.peer.chat_id = -lower_id;
                            }
                            hints.add(peer);
                        }
                        peer.rating += Math.exp(dtFinal / MessagesController.getInstance(currentAccount).ratingDecay);
                        Collections.sort(hints, new Comparator<TLRPC.TL_topPeer>() {
                            @Override
                            public int compare(TLRPC.TL_topPeer lhs, TLRPC.TL_topPeer rhs) {
                                if (lhs.rating > rhs.rating) {
                                    return -1;
                                } else if (lhs.rating < rhs.rating) {
                                    return 1;
                                }
                                return 0;
                            }
                        });

                        savePeer((int) did, 0, peer.rating);

                        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.reloadHints);
                    }
                });
            }
        });
    }

    private void savePeer(final int did, final int type, final double rating) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO chat_hints VALUES(?, ?, ?, ?)");
                    state.requery();
                    state.bindInteger(1, did);
                    state.bindInteger(2, type);
                    state.bindDouble(3, rating);
                    state.bindInteger(4, (int) System.currentTimeMillis() / 1000);
                    state.step();
                    state.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void deletePeer(final int did, final int type) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance(currentAccount).getDatabase().executeFast(String.format(Locale.US, "DELETE FROM chat_hints WHERE did = %d AND type = %d", did, type)).stepThis().dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private Intent createIntrnalShortcutIntent(long did) {
        Intent shortcutIntent = new Intent(ApplicationLoader.applicationContext, OpenChatReceiver.class);

        int lower_id = (int) did;
        int high_id = (int) (did >> 32);

        if (lower_id == 0) {
            shortcutIntent.putExtra("encId", high_id);
            TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
            if (encryptedChat == null) {
                return null;
            }
        } else if (lower_id > 0) {
            shortcutIntent.putExtra("userId", lower_id);
        } else if (lower_id < 0) {
            shortcutIntent.putExtra("chatId", -lower_id);
        } else {
            return null;
        }
        shortcutIntent.putExtra("currentAccount", currentAccount);
        shortcutIntent.setAction("com.tmessages.openchat" + did);
        shortcutIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        return shortcutIntent;
    }

    public void installShortcut(long did) {
        try {

            Intent shortcutIntent = createIntrnalShortcutIntent(did);

            int lower_id = (int) did;
            int high_id = (int) (did >> 32);

            TLRPC.User user = null;
            TLRPC.Chat chat = null;
            if (lower_id == 0) {
                TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                if (encryptedChat == null) {
                    return;
                }
                user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
            } else if (lower_id > 0) {
                user = MessagesController.getInstance(currentAccount).getUser(lower_id);
            } else if (lower_id < 0) {
                chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
            } else {
                return;
            }
            if (user == null && chat == null) {
                return;
            }

            String name;
            TLRPC.FileLocation photo = null;

            boolean selfUser = false;

            if (user != null) {
                if (UserObject.isUserSelf(user)) {
                    name = LocaleController.getString("SavedMessages", R.string.SavedMessages);
                    selfUser = true;
                } else {
                    name = ContactsController.formatName(user.first_name, user.last_name);
                    if (user.photo != null) {
                        photo = user.photo.photo_small;
                    }
                }
            } else {
                name = chat.title;
                if (chat.photo != null) {
                    photo = chat.photo.photo_small;
                }
            }

            Bitmap bitmap = null;
            if (selfUser || photo != null) {
                try {
                    if (!selfUser) {
                        File path = FileLoader.getPathToAttach(photo, true);
                        bitmap = BitmapFactory.decodeFile(path.toString());
                    }
                    if (selfUser || bitmap != null) {
                        int size = AndroidUtilities.dp(58);
                        Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                        result.eraseColor(Color.TRANSPARENT);
                        Canvas canvas = new Canvas(result);
                        if (selfUser) {
                            AvatarDrawable avatarDrawable = new AvatarDrawable(user);
                            avatarDrawable.setSavedMessages(1);
                            avatarDrawable.setBounds(0, 0, size, size);
                            avatarDrawable.draw(canvas);
                        } else {
                            BitmapShader shader = new BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                            if (roundPaint == null) {
                                roundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                                bitmapRect = new RectF();
                            }
                            float scale = size / (float) bitmap.getWidth();
                            canvas.save();
                            canvas.scale(scale, scale);
                            roundPaint.setShader(shader);
                            bitmapRect.set(0, 0, bitmap.getWidth(), bitmap.getHeight());
                            canvas.drawRoundRect(bitmapRect, bitmap.getWidth(), bitmap.getHeight(), roundPaint);
                            canvas.restore();
                        }
                        Drawable drawable = ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.book_logo);
                        int w = AndroidUtilities.dp(15);
                        int left = size - w - AndroidUtilities.dp(2);
                        int top = size - w - AndroidUtilities.dp(2);
                        drawable.setBounds(left, top, left + w, top + w);
                        drawable.draw(canvas);
                        try {
                            canvas.setBitmap(null);
                        } catch (Exception e) {
                            //don't promt, this will crash on 2.x
                        }
                        bitmap = result;
                    }
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
            if (Build.VERSION.SDK_INT >= 26) {
                ShortcutInfo.Builder pinShortcutInfo =
                        new ShortcutInfo.Builder(ApplicationLoader.applicationContext, "sdid_" + did)
                                .setShortLabel(name)
                                .setIntent(shortcutIntent);

                if (bitmap != null) {
                    pinShortcutInfo.setIcon(Icon.createWithBitmap(bitmap));
                } else {
                    if (user != null) {
                        if (user.bot) {
                            pinShortcutInfo.setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_bot));
                        } else {
                            pinShortcutInfo.setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_user));
                        }
                    } else if (chat != null) {
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            pinShortcutInfo.setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_channel));
                        } else {
                            pinShortcutInfo.setIcon(Icon.createWithResource(ApplicationLoader.applicationContext, R.drawable.book_group));
                        }
                    }
                }

                ShortcutManager shortcutManager = ApplicationLoader.applicationContext.getSystemService(ShortcutManager.class);
                shortcutManager.requestPinShortcut(pinShortcutInfo.build(), null);
            } else {
                Intent addIntent = new Intent();
                if (bitmap != null) {
                    addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON, bitmap);
                } else {
                    if (user != null) {
                        if (user.bot) {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_bot));
                        } else {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_user));
                        }
                    } else if (chat != null) {
                        if (ChatObject.isChannel(chat) && !chat.megagroup) {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_channel));
                        } else {
                            addIntent.putExtra(Intent.EXTRA_SHORTCUT_ICON_RESOURCE, Intent.ShortcutIconResource.fromContext(ApplicationLoader.applicationContext, R.drawable.book_group));
                        }
                    }
                }

                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent);
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
                addIntent.putExtra("duplicate", false);

                addIntent.setAction("com.android.launcher.action.INSTALL_SHORTCUT");
                ApplicationLoader.applicationContext.sendBroadcast(addIntent);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void uninstallShortcut(long did) {
        try {
            if (Build.VERSION.SDK_INT >= 26) {
                ShortcutManager shortcutManager = ApplicationLoader.applicationContext.getSystemService(ShortcutManager.class);
                ArrayList<String> arrayList = new ArrayList<>();
                arrayList.add("sdid_" + did);
                shortcutManager.removeDynamicShortcuts(arrayList);
            } else {
                int lower_id = (int) did;
                int high_id = (int) (did >> 32);

                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (lower_id == 0) {
                    TLRPC.EncryptedChat encryptedChat = MessagesController.getInstance(currentAccount).getEncryptedChat(high_id);
                    if (encryptedChat == null) {
                        return;
                    }
                    user = MessagesController.getInstance(currentAccount).getUser(encryptedChat.user_id);
                } else if (lower_id > 0) {
                    user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                } else if (lower_id < 0) {
                    chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                } else {
                    return;
                }
                if (user == null && chat == null) {
                    return;
                }
                String name;

                if (user != null) {
                    name = ContactsController.formatName(user.first_name, user.last_name);
                } else {
                    name = chat.title;
                }

                Intent addIntent = new Intent();
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_INTENT, createIntrnalShortcutIntent(did));
                addIntent.putExtra(Intent.EXTRA_SHORTCUT_NAME, name);
                addIntent.putExtra("duplicate", false);

                addIntent.setAction("com.android.launcher.action.UNINSTALL_SHORTCUT");
                ApplicationLoader.applicationContext.sendBroadcast(addIntent);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
    //---------------- SEARCH END ----------------

    private static Comparator<TLRPC.MessageEntity> entityComparator = new Comparator<TLRPC.MessageEntity>() {
        @Override
        public int compare(TLRPC.MessageEntity entity1, TLRPC.MessageEntity entity2) {
            if (entity1.offset > entity2.offset) {
                return 1;
            } else if (entity1.offset < entity2.offset) {
                return -1;
            }
            return 0;
        }
    };

    public MessageObject loadPinnedMessage(final int channelId, final int mid, boolean useQueue) {
        if (useQueue) {
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    loadPinnedMessageInternal(channelId, mid, false);
                }
            });
        } else {
            return loadPinnedMessageInternal(channelId, mid, true);
        }
        return null;
    }

    private MessageObject loadPinnedMessageInternal(final int channelId, final int mid, boolean returnValue) {
        try {
            long messageId = ((long) mid) | ((long) channelId) << 32;

            TLRPC.Message result = null;
            final ArrayList<TLRPC.User> users = new ArrayList<>();
            final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
            ArrayList<Integer> usersToLoad = new ArrayList<>();
            ArrayList<Integer> chatsToLoad = new ArrayList<>();

            SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages WHERE mid = %d", messageId));
            if (cursor.next()) {
                NativeByteBuffer data = cursor.byteBufferValue(0);
                if (data != null) {
                    result = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                    result.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                    data.reuse();
                    if (result.action instanceof TLRPC.TL_messageActionHistoryClear) {
                        result = null;
                    } else {
                        result.id = cursor.intValue(1);
                        result.date = cursor.intValue(2);
                        result.dialog_id = -channelId;
                        MessagesStorage.addUsersAndChatsFromMessage(result, usersToLoad, chatsToLoad);
                    }
                }
            }
            cursor.dispose();

            if (result == null) {
                cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM chat_pinned WHERE uid = %d", channelId));
                if (cursor.next()) {
                    NativeByteBuffer data = cursor.byteBufferValue(0);
                    if (data != null) {
                        result = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                        result.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                        data.reuse();
                        if (result.id != mid || result.action instanceof TLRPC.TL_messageActionHistoryClear) {
                            result = null;
                        } else {
                            result.dialog_id = -channelId;
                            MessagesStorage.addUsersAndChatsFromMessage(result, usersToLoad, chatsToLoad);
                        }
                    }
                }
                cursor.dispose();
            }

            if (result == null) {
                final TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                req.channel = MessagesController.getInstance(currentAccount).getInputChannel(channelId);
                req.id.add(mid);
                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                    @Override
                    public void run(TLObject response, TLRPC.TL_error error) {
                        boolean ok = false;
                        if (error == null) {
                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                            removeEmptyMessages(messagesRes.messages);
                            if (!messagesRes.messages.isEmpty()) {
                                ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                broadcastPinnedMessage(messagesRes.messages.get(0), messagesRes.users, messagesRes.chats, false, false);
                                MessagesStorage.getInstance(currentAccount).putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                savePinnedMessage(messagesRes.messages.get(0));
                                ok = true;
                            }
                        }
                        if (!ok) {
                            MessagesStorage.getInstance(currentAccount).updateChannelPinnedMessage(channelId, 0);
                        }
                    }
                });
            } else {
                if (returnValue) {
                    return broadcastPinnedMessage(result, users, chats, true, returnValue);
                } else {
                    if (!usersToLoad.isEmpty()) {
                        MessagesStorage.getInstance(currentAccount).getUsersInternal(TextUtils.join(",", usersToLoad), users);
                    }
                    if (!chatsToLoad.isEmpty()) {
                        MessagesStorage.getInstance(currentAccount).getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                    }
                    broadcastPinnedMessage(result, users, chats, true, false);
                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private void savePinnedMessage(final TLRPC.Message result) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance(currentAccount).getDatabase().beginTransaction();
                    SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO chat_pinned VALUES(?, ?, ?)");
                    NativeByteBuffer data = new NativeByteBuffer(result.getObjectSize());
                    result.serializeToStream(data);
                    state.requery();
                    state.bindInteger(1, result.to_id.channel_id);
                    state.bindInteger(2, result.id);
                    state.bindByteBuffer(3, data);
                    state.step();
                    data.reuse();
                    state.dispose();
                    MessagesStorage.getInstance(currentAccount).getDatabase().commitTransaction();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private MessageObject broadcastPinnedMessage(final TLRPC.Message result, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final boolean isCache, boolean returnValue) {
        final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
        for (int a = 0; a < users.size(); a++) {
            TLRPC.User user = users.get(a);
            usersDict.put(user.id, user);
        }
        final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();
        for (int a = 0; a < chats.size(); a++) {
            TLRPC.Chat chat = chats.get(a);
            chatsDict.put(chat.id, chat);
        }
        if (returnValue) {
            return new MessageObject(currentAccount, result, usersDict, chatsDict, false);
        } else {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    MessagesController.getInstance(currentAccount).putUsers(users, isCache);
                    MessagesController.getInstance(currentAccount).putChats(chats, isCache);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didLoadedPinnedMessage, new MessageObject(currentAccount, result, usersDict, chatsDict, false));
                }
            });
        }
        return null;
    }

    private static void removeEmptyMessages(ArrayList<TLRPC.Message> messages) {
        for (int a = 0; a < messages.size(); a++) {
            TLRPC.Message message = messages.get(a);
            if (message == null || message instanceof TLRPC.TL_messageEmpty || message.action instanceof TLRPC.TL_messageActionHistoryClear) {
                messages.remove(a);
                a--;
            }
        }
    }

    public void loadReplyMessagesForMessages(final ArrayList<MessageObject> messages, final long dialogId) {
        if ((int) dialogId == 0) {
            final ArrayList<Long> replyMessages = new ArrayList<>();
            final LongSparseArray<ArrayList<MessageObject>> replyMessageRandomOwners = new LongSparseArray<>();
            for (int a = 0; a < messages.size(); a++) {
                MessageObject messageObject = messages.get(a);
                if (messageObject.isReply() && messageObject.replyMessageObject == null) {
                    long id = messageObject.messageOwner.reply_to_random_id;
                    ArrayList<MessageObject> messageObjects = replyMessageRandomOwners.get(id);
                    if (messageObjects == null) {
                        messageObjects = new ArrayList<>();
                        replyMessageRandomOwners.put(id, messageObjects);
                    }
                    messageObjects.add(messageObject);
                    if (!replyMessages.contains(id)) {
                        replyMessages.add(id);
                    }
                }
            }
            if (replyMessages.isEmpty()) {
                return;
            }

            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT m.data, m.mid, m.date, r.random_id FROM randoms as r INNER JOIN messages as m ON r.mid = m.mid WHERE r.random_id IN(%s)", TextUtils.join(",", replyMessages)));
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                                data.reuse();
                                message.id = cursor.intValue(1);
                                message.date = cursor.intValue(2);
                                message.dialog_id = dialogId;

                                long value = cursor.longValue(3);
                                ArrayList<MessageObject> arrayList = replyMessageRandomOwners.get(value);
                                replyMessageRandomOwners.remove(value);
                                if (arrayList != null) {
                                    MessageObject messageObject = new MessageObject(currentAccount, message, false);
                                    for (int b = 0; b < arrayList.size(); b++) {
                                        MessageObject object = arrayList.get(b);
                                        object.replyMessageObject = messageObject;
                                        object.messageOwner.reply_to_msg_id = messageObject.getId();
                                        if (object.isMegagroup()) {
                                            object.replyMessageObject.messageOwner.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                                        }
                                    }
                                }
                            }
                        }
                        cursor.dispose();
                        if (replyMessageRandomOwners.size() != 0) {
                            for (int b = 0; b < replyMessageRandomOwners.size(); b++) {
                                ArrayList<MessageObject> arrayList = replyMessageRandomOwners.valueAt(b);
                                for (int a = 0; a < arrayList.size(); a++) {
                                    arrayList.get(a).messageOwner.reply_to_random_id = 0;
                                }
                            }
                        }
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didLoadedReplyMessages, dialogId);
                            }
                        });
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        } else {
            final ArrayList<Integer> replyMessages = new ArrayList<>();
            final SparseArray<ArrayList<MessageObject>> replyMessageOwners = new SparseArray<>();
            final StringBuilder stringBuilder = new StringBuilder();
            int channelId = 0;
            for (int a = 0; a < messages.size(); a++) {
                MessageObject messageObject = messages.get(a);
                if (messageObject.getId() > 0 && messageObject.isReply() && messageObject.replyMessageObject == null) {
                    int id = messageObject.messageOwner.reply_to_msg_id;
                    long messageId = id;
                    if (messageObject.messageOwner.to_id.channel_id != 0) {
                        messageId |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
                        channelId = messageObject.messageOwner.to_id.channel_id;
                    }
                    if (stringBuilder.length() > 0) {
                        stringBuilder.append(',');
                    }
                    stringBuilder.append(messageId);
                    ArrayList<MessageObject> messageObjects = replyMessageOwners.get(id);
                    if (messageObjects == null) {
                        messageObjects = new ArrayList<>();
                        replyMessageOwners.put(id, messageObjects);
                    }
                    messageObjects.add(messageObject);
                    if (!replyMessages.contains(id)) {
                        replyMessages.add(id);
                    }
                }
            }
            if (replyMessages.isEmpty()) {
                return;
            }

            final int channelIdFinal = channelId;
            MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                @Override
                public void run() {
                    try {
                        final ArrayList<TLRPC.Message> result = new ArrayList<>();
                        final ArrayList<TLRPC.User> users = new ArrayList<>();
                        final ArrayList<TLRPC.Chat> chats = new ArrayList<>();
                        ArrayList<Integer> usersToLoad = new ArrayList<>();
                        ArrayList<Integer> chatsToLoad = new ArrayList<>();

                        SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data, mid, date FROM messages WHERE mid IN(%s)", stringBuilder.toString()));
                        while (cursor.next()) {
                            NativeByteBuffer data = cursor.byteBufferValue(0);
                            if (data != null) {
                                TLRPC.Message message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                                data.reuse();
                                message.id = cursor.intValue(1);
                                message.date = cursor.intValue(2);
                                message.dialog_id = dialogId;
                                MessagesStorage.addUsersAndChatsFromMessage(message, usersToLoad, chatsToLoad);
                                result.add(message);
                                replyMessages.remove((Integer) message.id);
                            }
                        }
                        cursor.dispose();

                        if (!usersToLoad.isEmpty()) {
                            MessagesStorage.getInstance(currentAccount).getUsersInternal(TextUtils.join(",", usersToLoad), users);
                        }
                        if (!chatsToLoad.isEmpty()) {
                            MessagesStorage.getInstance(currentAccount).getChatsInternal(TextUtils.join(",", chatsToLoad), chats);
                        }
                        broadcastReplyMessages(result, replyMessageOwners, users, chats, dialogId, true);

                        if (!replyMessages.isEmpty()) {
                            if (channelIdFinal != 0) {
                                final TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                                req.channel = MessagesController.getInstance(currentAccount).getInputChannel(channelIdFinal);
                                req.id = replyMessages;
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {
                                        if (error == null) {
                                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                            removeEmptyMessages(messagesRes.messages);
                                            ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                            broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false);
                                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                            saveReplyMessages(replyMessageOwners, messagesRes.messages);
                                        }
                                    }
                                });
                            } else {
                                TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                                req.id = replyMessages;
                                ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                                    @Override
                                    public void run(TLObject response, TLRPC.TL_error error) {
                                        if (error == null) {
                                            TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                            removeEmptyMessages(messagesRes.messages);
                                            ImageLoader.saveMessagesThumbs(messagesRes.messages);
                                            broadcastReplyMessages(messagesRes.messages, replyMessageOwners, messagesRes.users, messagesRes.chats, dialogId, false);
                                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(messagesRes.users, messagesRes.chats, true, true);
                                            saveReplyMessages(replyMessageOwners, messagesRes.messages);
                                        }
                                    }
                                });
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            });
        }
    }

    private void saveReplyMessages(final SparseArray<ArrayList<MessageObject>> replyMessageOwners, final ArrayList<TLRPC.Message> result) {
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    MessagesStorage.getInstance(currentAccount).getDatabase().beginTransaction();
                    SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("UPDATE messages SET replydata = ? WHERE mid = ?");
                    for (int a = 0; a < result.size(); a++) {
                        TLRPC.Message message = result.get(a);
                        ArrayList<MessageObject> messageObjects = replyMessageOwners.get(message.id);
                        if (messageObjects != null) {
                            NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
                            message.serializeToStream(data);
                            for (int b = 0; b < messageObjects.size(); b++) {
                                MessageObject messageObject = messageObjects.get(b);
                                state.requery();
                                long messageId = messageObject.getId();
                                if (messageObject.messageOwner.to_id.channel_id != 0) {
                                    messageId |= ((long) messageObject.messageOwner.to_id.channel_id) << 32;
                                }
                                state.bindByteBuffer(1, data);
                                state.bindLong(2, messageId);
                                state.step();
                            }
                            data.reuse();
                        }
                    }
                    state.dispose();
                    MessagesStorage.getInstance(currentAccount).getDatabase().commitTransaction();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    private void broadcastReplyMessages(final ArrayList<TLRPC.Message> result, final SparseArray<ArrayList<MessageObject>> replyMessageOwners, final ArrayList<TLRPC.User> users, final ArrayList<TLRPC.Chat> chats, final long dialog_id, final boolean isCache) {
        final SparseArray<TLRPC.User> usersDict = new SparseArray<>();
        for (int a = 0; a < users.size(); a++) {
            TLRPC.User user = users.get(a);
            usersDict.put(user.id, user);
        }
        final SparseArray<TLRPC.Chat> chatsDict = new SparseArray<>();
        for (int a = 0; a < chats.size(); a++) {
            TLRPC.Chat chat = chats.get(a);
            chatsDict.put(chat.id, chat);
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                MessagesController.getInstance(currentAccount).putUsers(users, isCache);
                MessagesController.getInstance(currentAccount).putChats(chats, isCache);
                boolean changed = false;
                for (int a = 0; a < result.size(); a++) {
                    TLRPC.Message message = result.get(a);
                    ArrayList<MessageObject> arrayList = replyMessageOwners.get(message.id);
                    if (arrayList != null) {
                        MessageObject messageObject = new MessageObject(currentAccount, message, usersDict, chatsDict, false);
                        for (int b = 0; b < arrayList.size(); b++) {
                            MessageObject m = arrayList.get(b);
                            m.replyMessageObject = messageObject;
                            if (m.messageOwner.action instanceof TLRPC.TL_messageActionPinMessage) {
                                m.generatePinMessageText(null, null);
                            } else if (m.messageOwner.action instanceof TLRPC.TL_messageActionGameScore) {
                                m.generateGameMessageText(null);
                            } else if (m.messageOwner.action instanceof TLRPC.TL_messageActionPaymentSent) {
                                m.generatePaymentSentMessageText(null);
                            }
                            if (m.isMegagroup()) {
                                m.replyMessageObject.messageOwner.flags |= TLRPC.MESSAGE_FLAG_MEGAGROUP;
                            }
                        }
                        changed = true;
                    }
                }
                if (changed) {
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.didLoadedReplyMessages, dialog_id);
                }
            }
        });
    }

    public static void sortEntities(ArrayList<TLRPC.MessageEntity> entities) {
        Collections.sort(entities, entityComparator);
    }

    private static boolean checkInclusion(int index, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset <= index && entity.offset + entity.length > index) {
                return true;
            }
        }
        return false;
    }

    private static boolean checkIntersection(int start, int end, ArrayList<TLRPC.MessageEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return false;
        }
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset > start && entity.offset + entity.length <= end) {
                return true;
            }
        }
        return false;
    }

    private static void removeOffsetAfter(int start, int countToRemove, ArrayList<TLRPC.MessageEntity> entities) {
        int count = entities.size();
        for (int a = 0; a < count; a++) {
            TLRPC.MessageEntity entity = entities.get(a);
            if (entity.offset > start) {
                entity.offset -= countToRemove;
            }
        }
    }

    public CharSequence substring(CharSequence source, int start, int end) {
        if (source instanceof SpannableStringBuilder) {
            return ((SpannableStringBuilder) source).subSequence(start, end);
        } else if (source instanceof SpannedString) {
            return ((SpannedString) source).subSequence(start, end);
        } else {
            return TextUtils.substring(source, start, end);
        }
    }

    public ArrayList<TLRPC.MessageEntity> getEntities(CharSequence[] message) {
        if (message == null || message[0] == null) {
            return null;
        }
        ArrayList<TLRPC.MessageEntity> entities = null;
        int index;
        int start = -1;
        int lastIndex = 0;
        boolean isPre = false;
        final String mono = "`";
        final String pre = "```";
        final String bold = "**";
        final String italic = "__";
        while ((index = TextUtils.indexOf(message[0], !isPre ? mono : pre, lastIndex)) != -1) {
            if (start == -1) {
                isPre = message[0].length() - index > 2 && message[0].charAt(index + 1) == '`' && message[0].charAt(index + 2) == '`';
                start = index;
                lastIndex = index + (isPre ? 3 : 1);
            } else {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int a = index + (isPre ? 3 : 1); a < message[0].length(); a++) {
                    if (message[0].charAt(a) == '`') {
                        index++;
                    } else {
                        break;
                    }
                }
                lastIndex = index + (isPre ? 3 : 1);
                if (isPre) {
                    int firstChar = start > 0 ? message[0].charAt(start - 1) : 0;
                    boolean replacedFirst = firstChar == ' ' || firstChar == '\n';
                    CharSequence startMessage = substring(message[0], 0, start - (replacedFirst ? 1 : 0));
                    CharSequence content = substring(message[0], start + 3, index);
                    firstChar = index + 3 < message[0].length() ? message[0].charAt(index + 3) : 0;
                    CharSequence endMessage = substring(message[0], index + 3 + (firstChar == ' ' || firstChar == '\n' ? 1 : 0), message[0].length());
                    if (startMessage.length() != 0) {
                        startMessage = TextUtils.concat(startMessage, "\n");
                    } else {
                        replacedFirst = true;
                    }
                    if (endMessage.length() != 0) {
                        endMessage = TextUtils.concat("\n", endMessage);
                    }
                    if (!TextUtils.isEmpty(content)) {
                        message[0] = TextUtils.concat(startMessage, content, endMessage);
                        TLRPC.TL_messageEntityPre entity = new TLRPC.TL_messageEntityPre();
                        entity.offset = start + (replacedFirst ? 0 : 1);
                        entity.length = index - start - 3 + (replacedFirst ? 0 : 1);
                        entity.language = "";
                        entities.add(entity);
                        lastIndex -= 6;
                    }
                } else {
                    if (start + 1 != index) {
                        message[0] = TextUtils.concat(substring(message[0], 0, start), substring(message[0], start + 1, index), substring(message[0], index + 1, message[0].length()));
                        TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
                        entity.offset = start;
                        entity.length = index - start - 1;
                        entities.add(entity);
                        lastIndex -= 2;
                    }
                }
                start = -1;
                isPre = false;
            }
        }
        if (start != -1 && isPre) {
            message[0] = TextUtils.concat(substring(message[0], 0, start), substring(message[0], start + 2, message[0].length()));
            if (entities == null) {
                entities = new ArrayList<>();
            }
            TLRPC.TL_messageEntityCode entity = new TLRPC.TL_messageEntityCode();
            entity.offset = start;
            entity.length = 1;
            entities.add(entity);
        }

        if (message[0] instanceof Spanned) {
            Spanned spannable = (Spanned) message[0];
            TypefaceSpan spans[] = spannable.getSpans(0, message[0].length(), TypefaceSpan.class);
            if (spans != null && spans.length > 0) {
                for (int a = 0; a < spans.length; a++) {
                    TypefaceSpan span = spans[a];
                    int spanStart = spannable.getSpanStart(span);
                    int spanEnd = spannable.getSpanEnd(span);
                    if (checkInclusion(spanStart, entities) || checkInclusion(spanEnd, entities) || checkIntersection(spanStart, spanEnd, entities)) {
                        continue;
                    }
                    if (entities == null) {
                        entities = new ArrayList<>();
                    }
                    TLRPC.MessageEntity entity;
                    if (span.isMono()) {
                        entity = new TLRPC.TL_messageEntityCode();
                    } else if (span.isBold()) {
                        entity = new TLRPC.TL_messageEntityBold();
                    } else {
                        entity = new TLRPC.TL_messageEntityItalic();
                    }
                    entity.offset = spanStart;
                    entity.length = spanEnd - spanStart;
                    entities.add(entity);
                }
            }

            URLSpanUserMention spansMentions[] = spannable.getSpans(0, message[0].length(), URLSpanUserMention.class);
            if (spansMentions != null && spansMentions.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < spansMentions.length; b++) {
                    TLRPC.TL_inputMessageEntityMentionName entity = new TLRPC.TL_inputMessageEntityMentionName();
                    entity.user_id = MessagesController.getInstance(currentAccount).getInputUser(Utilities.parseInt(spansMentions[b].getURL()));
                    if (entity.user_id != null) {
                        entity.offset = spannable.getSpanStart(spansMentions[b]);
                        entity.length = Math.min(spannable.getSpanEnd(spansMentions[b]), message[0].length()) - entity.offset;
                        if (message[0].charAt(entity.offset + entity.length - 1) == ' ') {
                            entity.length--;
                        }
                        entities.add(entity);
                    }
                }
            }

            URLSpanReplacement spansUrlReplacement[] = spannable.getSpans(0, message[0].length(), URLSpanReplacement.class);
            if (spansUrlReplacement != null && spansUrlReplacement.length > 0) {
                if (entities == null) {
                    entities = new ArrayList<>();
                }
                for (int b = 0; b < spansUrlReplacement.length; b++) {
                    TLRPC.TL_messageEntityTextUrl entity = new TLRPC.TL_messageEntityTextUrl();
                    entity.offset = spannable.getSpanStart(spansUrlReplacement[b]);
                    entity.length = Math.min(spannable.getSpanEnd(spansUrlReplacement[b]), message[0].length()) - entity.offset;
                    entity.url = spansUrlReplacement[b].getURL();
                    entities.add(entity);
                }
            }
        }

        for (int c = 0; c < 2; c++) {
            lastIndex = 0;
            start = -1;
            String checkString = c == 0 ? bold : italic;
            char checkChar = c == 0 ? '*' : '_';
            while ((index = TextUtils.indexOf(message[0], checkString, lastIndex)) != -1) {
                if (start == -1) {
                    char prevChar = index == 0 ? ' ' : message[0].charAt(index - 1);
                    if (!checkInclusion(index, entities) && (prevChar == ' ' || prevChar == '\n')) {
                        start = index;
                    }
                    lastIndex = index + 2;
                } else {
                    for (int a = index + 2; a < message[0].length(); a++) {
                        if (message[0].charAt(a) == checkChar) {
                            index++;
                        } else {
                            break;
                        }
                    }
                    lastIndex = index + 2;
                    if (checkInclusion(index, entities) || checkIntersection(start, index, entities)) {
                        start = -1;
                        continue;
                    }
                    if (start + 2 != index) {
                        if (entities == null) {
                            entities = new ArrayList<>();
                        }
                        try {
                            message[0] = TextUtils.concat(substring(message[0], 0, start), substring(message[0], start + 2, index), substring(message[0], index + 2, message[0].length()));
                        } catch (Exception e) {
                            message[0] = substring(message[0], 0, start).toString() + substring(message[0], start + 2, index).toString() + substring(message[0], index + 2, message[0].length()).toString();
                        }

                        TLRPC.MessageEntity entity;
                        if (c == 0) {
                            entity = new TLRPC.TL_messageEntityBold();
                        } else {
                            entity = new TLRPC.TL_messageEntityItalic();
                        }
                        entity.offset = start;
                        entity.length = index - start - 2;
                        removeOffsetAfter(entity.offset + entity.length, 4, entities);
                        entities.add(entity);
                        lastIndex -= 4;
                    }
                    start = -1;
                }
            }
        }

        return entities;
    }

    //---------------- MESSAGES END ----------------

    private LongSparseArray<TLRPC.DraftMessage> drafts = new LongSparseArray<>();
    private LongSparseArray<TLRPC.Message> draftMessages = new LongSparseArray<>();
    private boolean inTransaction;
    private SharedPreferences preferences;
    private boolean loadingDrafts;

    public void loadDrafts() {
        if (UserConfig.getInstance(currentAccount).draftsLoaded || loadingDrafts) {
            return;
        }
        loadingDrafts = true;
        TLRPC.TL_messages_getAllDrafts req = new TLRPC.TL_messages_getAllDrafts();
        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
            @Override
            public void run(TLObject response, TLRPC.TL_error error) {
                if (error != null) {
                    return;
                }
                MessagesController.getInstance(currentAccount).processUpdates((TLRPC.Updates) response, false);
                AndroidUtilities.runOnUIThread(new Runnable() {
                    @Override
                    public void run() {
                        UserConfig.getInstance(currentAccount).draftsLoaded = true;
                        loadingDrafts = false;
                        UserConfig.getInstance(currentAccount).saveConfig(false);
                    }
                });
            }
        });
    }

    public TLRPC.DraftMessage getDraft(long did) {
        return drafts.get(did);
    }

    public TLRPC.Message getDraftMessage(long did) {
        return draftMessages.get(did);
    }

    public void saveDraft(long did, CharSequence message, ArrayList<TLRPC.MessageEntity> entities, TLRPC.Message replyToMessage, boolean noWebpage) {
        saveDraft(did, message, entities, replyToMessage, noWebpage, false);
    }

    public void saveDraft(long did, CharSequence message, ArrayList<TLRPC.MessageEntity> entities, TLRPC.Message replyToMessage, boolean noWebpage, boolean clean) {
        TLRPC.DraftMessage draftMessage;
        if (!TextUtils.isEmpty(message) || replyToMessage != null) {
            draftMessage = new TLRPC.TL_draftMessage();
        } else {
            draftMessage = new TLRPC.TL_draftMessageEmpty();
        }
        draftMessage.date = (int) (System.currentTimeMillis() / 1000);
        draftMessage.message = message == null ? "" : message.toString();
        draftMessage.no_webpage = noWebpage;
        if (replyToMessage != null) {
            draftMessage.reply_to_msg_id = replyToMessage.id;
            draftMessage.flags |= 1;
        }
        if (entities != null && !entities.isEmpty()) {
            draftMessage.entities = entities;
            draftMessage.flags |= 8;
        }

        TLRPC.DraftMessage currentDraft = drafts.get(did);
        if (!clean) {
            if (currentDraft != null && currentDraft.message.equals(draftMessage.message) && currentDraft.reply_to_msg_id == draftMessage.reply_to_msg_id && currentDraft.no_webpage == draftMessage.no_webpage ||
                    currentDraft == null && TextUtils.isEmpty(draftMessage.message) && draftMessage.reply_to_msg_id == 0) {
                return;
            }
        }

        saveDraft(did, draftMessage, replyToMessage, false);
        int lower_id = (int) did;
        if (lower_id != 0) {
            TLRPC.TL_messages_saveDraft req = new TLRPC.TL_messages_saveDraft();
            req.peer = MessagesController.getInstance(currentAccount).getInputPeer(lower_id);
            if (req.peer == null) {
                return;
            }
            req.message = draftMessage.message;
            req.no_webpage = draftMessage.no_webpage;
            req.reply_to_msg_id = draftMessage.reply_to_msg_id;
            req.entities = draftMessage.entities;
            req.flags = draftMessage.flags;
            ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                @Override
                public void run(TLObject response, TLRPC.TL_error error) {

                }
            });
        }
        MessagesController.getInstance(currentAccount).sortDialogs(null);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public void saveDraft(final long did, TLRPC.DraftMessage draft, TLRPC.Message replyToMessage, boolean fromServer) {
        SharedPreferences.Editor editor = preferences.edit();
        if (draft == null || draft instanceof TLRPC.TL_draftMessageEmpty) {
            drafts.remove(did);
            draftMessages.remove(did);
            preferences.edit().remove("" + did).remove("r_" + did).commit();
        } else {
            drafts.put(did, draft);
            try {
                SerializedData serializedData = new SerializedData(draft.getObjectSize());
                draft.serializeToStream(serializedData);
                editor.putString("" + did, Utilities.bytesToHex(serializedData.toByteArray()));
                serializedData.cleanup();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        if (replyToMessage == null) {
            draftMessages.remove(did);
            editor.remove("r_" + did);
        } else {
            draftMessages.put(did, replyToMessage);
            SerializedData serializedData = new SerializedData(replyToMessage.getObjectSize());
            replyToMessage.serializeToStream(serializedData);
            editor.putString("r_" + did, Utilities.bytesToHex(serializedData.toByteArray()));
            serializedData.cleanup();
        }
        editor.commit();
        if (fromServer) {
            if (draft.reply_to_msg_id != 0 && replyToMessage == null) {
                int lower_id = (int) did;
                TLRPC.User user = null;
                TLRPC.Chat chat = null;
                if (lower_id > 0) {
                    user = MessagesController.getInstance(currentAccount).getUser(lower_id);
                } else {
                    chat = MessagesController.getInstance(currentAccount).getChat(-lower_id);
                }
                if (user != null || chat != null) {
                    long messageId = draft.reply_to_msg_id;
                    final int channelIdFinal;
                    if (ChatObject.isChannel(chat)) {
                        messageId |= ((long) chat.id) << 32;
                        channelIdFinal = chat.id;
                    } else {
                        channelIdFinal = 0;
                    }
                    final long messageIdFinal = messageId;

                    MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                TLRPC.Message message = null;
                                SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT data FROM messages WHERE mid = %d", messageIdFinal));
                                if (cursor.next()) {
                                    NativeByteBuffer data = cursor.byteBufferValue(0);
                                    if (data != null) {
                                        message = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                        message.readAttachPath(data, UserConfig.getInstance(currentAccount).clientUserId);
                                        data.reuse();
                                    }
                                }
                                cursor.dispose();
                                if (message == null) {
                                    if (channelIdFinal != 0) {
                                        final TLRPC.TL_channels_getMessages req = new TLRPC.TL_channels_getMessages();
                                        req.channel = MessagesController.getInstance(currentAccount).getInputChannel(channelIdFinal);
                                        req.id.add((int) messageIdFinal);
                                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                                            @Override
                                            public void run(TLObject response, TLRPC.TL_error error) {
                                                if (error == null) {
                                                    TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                                    if (!messagesRes.messages.isEmpty()) {
                                                        saveDraftReplyMessage(did, messagesRes.messages.get(0));
                                                    }
                                                }
                                            }
                                        });
                                    } else {
                                        TLRPC.TL_messages_getMessages req = new TLRPC.TL_messages_getMessages();
                                        req.id.add((int) messageIdFinal);
                                        ConnectionsManager.getInstance(currentAccount).sendRequest(req, new RequestDelegate() {
                                            @Override
                                            public void run(TLObject response, TLRPC.TL_error error) {
                                                if (error == null) {
                                                    TLRPC.messages_Messages messagesRes = (TLRPC.messages_Messages) response;
                                                    if (!messagesRes.messages.isEmpty()) {
                                                        saveDraftReplyMessage(did, messagesRes.messages.get(0));
                                                    }
                                                }
                                            }
                                        });
                                    }
                                } else {
                                    saveDraftReplyMessage(did, message);
                                }
                            } catch (Exception e) {
                                FileLog.e(e);
                            }
                        }
                    });
                }
            }
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.newDraftReceived, did);
        }
    }

    private void saveDraftReplyMessage(final long did, final TLRPC.Message message) {
        if (message == null) {
            return;
        }
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                TLRPC.DraftMessage draftMessage = drafts.get(did);
                if (draftMessage != null && draftMessage.reply_to_msg_id == message.id) {
                    draftMessages.put(did, message);
                    SerializedData serializedData = new SerializedData(message.getObjectSize());
                    message.serializeToStream(serializedData);
                    preferences.edit().putString("r_" + did, Utilities.bytesToHex(serializedData.toByteArray())).commit();
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.newDraftReceived, did);
                    serializedData.cleanup();
                }
            }
        });
    }

    public void clearAllDrafts() {
        drafts.clear();
        draftMessages.clear();
        preferences.edit().clear().commit();
        MessagesController.getInstance(currentAccount).sortDialogs(null);
        NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
    }

    public void cleanDraft(long did, boolean replyOnly) {
        TLRPC.DraftMessage draftMessage = drafts.get(did);
        if (draftMessage == null) {
            return;
        }
        if (!replyOnly) {
            drafts.remove(did);
            draftMessages.remove(did);
            preferences.edit().remove("" + did).remove("r_" + did).commit();
            MessagesController.getInstance(currentAccount).sortDialogs(null);
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.dialogsNeedReload);
        } else if (draftMessage.reply_to_msg_id != 0) {
            draftMessage.reply_to_msg_id = 0;
            draftMessage.flags &= ~1;
            saveDraft(did, draftMessage.message, draftMessage.entities, null, draftMessage.no_webpage, true);
        }
    }

    public void beginTransaction() {
        inTransaction = true;
    }

    public void endTransaction() {
        inTransaction = false;
    }

    //---------------- DRAFT END ----------------

    private SparseArray<TLRPC.BotInfo> botInfos = new SparseArray<>();
    private LongSparseArray<TLRPC.Message> botKeyboards = new LongSparseArray<>();
    private SparseLongArray botKeyboardsByMids = new SparseLongArray();

    public void clearBotKeyboard(final long did, final ArrayList<Integer> messages) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (messages != null) {
                    for (int a = 0; a < messages.size(); a++) {
                        long did = botKeyboardsByMids.get(messages.get(a));
                        if (did != 0) {
                            botKeyboards.remove(did);
                            botKeyboardsByMids.delete(messages.get(a));
                            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botKeyboardDidLoaded, null, did);
                        }
                    }
                } else {
                    botKeyboards.remove(did);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botKeyboardDidLoaded, null, did);
                }
            }
        });
    }

    public void loadBotKeyboard(final long did) {
        TLRPC.Message keyboard = botKeyboards.get(did);
        if (keyboard != null) {
            NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botKeyboardDidLoaded, keyboard, did);
            return;
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    TLRPC.Message botKeyboard = null;
                    SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT info FROM bot_keyboard WHERE uid = %d", did));
                    if (cursor.next()) {
                        NativeByteBuffer data;

                        if (!cursor.isNull(0)) {
                            data = cursor.byteBufferValue(0);
                            if (data != null) {
                                botKeyboard = TLRPC.Message.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                            }
                        }
                    }
                    cursor.dispose();

                    if (botKeyboard != null) {
                        final TLRPC.Message botKeyboardFinal = botKeyboard;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botKeyboardDidLoaded, botKeyboardFinal, did);
                            }
                        });
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void loadBotInfo(final int uid, boolean cache, final int classGuid) {
        if (cache) {
            TLRPC.BotInfo botInfo = botInfos.get(uid);
            if (botInfo != null) {
                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botInfoDidLoaded, botInfo, classGuid);
                return;
            }
        }
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    TLRPC.BotInfo botInfo = null;
                    SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT info FROM bot_info WHERE uid = %d", uid));
                    if (cursor.next()) {
                        NativeByteBuffer data;

                        if (!cursor.isNull(0)) {
                            data = cursor.byteBufferValue(0);
                            if (data != null) {
                                botInfo = TLRPC.BotInfo.TLdeserialize(data, data.readInt32(false), false);
                                data.reuse();
                            }
                        }
                    }
                    cursor.dispose();

                    if (botInfo != null) {
                        final TLRPC.BotInfo botInfoFinal = botInfo;
                        AndroidUtilities.runOnUIThread(new Runnable() {
                            @Override
                            public void run() {
                                NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botInfoDidLoaded, botInfoFinal, classGuid);
                            }
                        });
                    }
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    public void putBotKeyboard(final long did, final TLRPC.Message message) {
        if (message == null) {
            return;
        }
        try {
            int mid = 0;
            SQLiteCursor cursor = MessagesStorage.getInstance(currentAccount).getDatabase().queryFinalized(String.format(Locale.US, "SELECT mid FROM bot_keyboard WHERE uid = %d", did));
            if (cursor.next()) {
                mid = cursor.intValue(0);
            }
            cursor.dispose();
            if (mid >= message.id) {
                return;
            }

            SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO bot_keyboard VALUES(?, ?, ?)");
            state.requery();
            NativeByteBuffer data = new NativeByteBuffer(message.getObjectSize());
            message.serializeToStream(data);
            state.bindLong(1, did);
            state.bindInteger(2, message.id);
            state.bindByteBuffer(3, data);
            state.step();
            data.reuse();
            state.dispose();

            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    TLRPC.Message old = botKeyboards.get(did);
                    botKeyboards.put(did, message);
                    if (old != null) {
                        botKeyboardsByMids.delete(old.id);
                    }
                    botKeyboardsByMids.put(message.id, did);
                    NotificationCenter.getInstance(currentAccount).postNotificationName(NotificationCenter.botKeyboardDidLoaded, message, did);
                }
            });
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public void putBotInfo(final TLRPC.BotInfo botInfo) {
        if (botInfo == null) {
            return;
        }
        botInfos.put(botInfo.user_id, botInfo);
        MessagesStorage.getInstance(currentAccount).getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state = MessagesStorage.getInstance(currentAccount).getDatabase().executeFast("REPLACE INTO bot_info(uid, info) VALUES(?, ?)");
                    state.requery();
                    NativeByteBuffer data = new NativeByteBuffer(botInfo.getObjectSize());
                    botInfo.serializeToStream(data);
                    state.bindInteger(1, botInfo.user_id);
                    state.bindByteBuffer(2, data);
                    state.step();
                    data.reuse();
                    state.dispose();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
        });
    }

    //---------------- BOT END ----------------
}
