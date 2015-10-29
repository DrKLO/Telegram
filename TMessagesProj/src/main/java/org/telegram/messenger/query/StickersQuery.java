/*
 * This is the source code of Telegram for Android v. 3.x.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.messenger.query;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Message;
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
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.StickersAlert;

import java.util.ArrayList;
import java.util.HashMap;

public class StickersQuery {

    private static String loadHash;
    private static int loadDate;
    private static ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
    private static HashMap<Long, TLRPC.TL_messages_stickerSet> stickerSetsById = new HashMap<>();
    private static HashMap<Long, String> stickersByEmoji = new HashMap<>();
    private static HashMap<Long, TLRPC.Document> stickersById = new HashMap<>();
    private static HashMap<String, ArrayList<TLRPC.Document>> allStickers = new HashMap<>();

    private static boolean loadingStickers;
    private static boolean stickersLoaded;

    public static void cleanup() {
        loadHash = null;
        loadDate = 0;
        allStickers.clear();
        stickerSets.clear();
        stickersByEmoji.clear();
        stickerSetsById.clear();
        loadingStickers = false;
        stickersLoaded = false;
    }

    public static void checkStickers() {
        if (!loadingStickers && (!stickersLoaded || loadDate < (System.currentTimeMillis() / 1000 - 60 * 60))) {
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
            if (stickerSet != null && (stickerSet.set.flags & 2) != 0) {
                return null;
            }
        }
        return document;
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

    public static String getEmojiForSticker(long id) {
        String value = stickersByEmoji.get(id);
        return value != null ? value : "";
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
                    String hash = null;
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
                            hash = cursor.stringValue(2);
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
            TLRPC.TL_messages_getAllStickers req = new TLRPC.TL_messages_getAllStickers();
            req.hash = loadHash == null || force ? "" : loadHash;
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
                                        oldSet.set.flags = stickerSet.flags;
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
                                processLoadedStickers(null, false, (int) (System.currentTimeMillis() / 1000), error == null ? "" : null);
                            }
                        }
                    });
                }
            });
        }
    }

    private static void putStickersToCache(final ArrayList<TLRPC.TL_messages_stickerSet> stickers, final int date, final String hash) {
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO stickers_v2 VALUES(?, ?, ?, ?)");
                    state.requery();
                    int size = 4;
                    for (int a = 0; a < stickers.size(); a++) {
                        size += stickers.get(a).getObjectSize();
                    }
                    NativeByteBuffer data = new NativeByteBuffer(size);
                    data.writeInt32(stickers.size());
                    for (int a = 0; a < stickers.size(); a++) {
                        stickers.get(a).serializeToStream(data);
                    }
                    state.bindInteger(1, 1);
                    state.bindByteBuffer(2, data);
                    state.bindInteger(3, date);
                    state.bindString(4, hash);
                    state.step();
                    data.reuse();
                    state.dispose();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public static long getStickerSetId(TLRPC.Document document) {
        for (TLRPC.DocumentAttribute attribute : document.attributes) {
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.stickerset instanceof TLRPC.TL_inputStickerSetID) {
                    return attribute.stickerset.id;
                }
                break;
            }
        }
        return -1;
    }

    private static void processLoadedStickers(final ArrayList<TLRPC.TL_messages_stickerSet> res, final boolean cache, final int date, final String hash) {
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
                if (cache && (res == null || date < (int) (System.currentTimeMillis() / 1000 - 60 * 60)) || !cache && res == null && hash == null) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            if (res != null && hash != null) {
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

                            for (int b = 0; b < stickerSet.documents.size(); b++) {
                                TLRPC.Document document = stickerSet.documents.get(b);
                                if (document == null || document instanceof TLRPC.TL_documentEmpty) {
                                    continue;
                                }
                                stickersByIdNew.put(document.id, document);
                            }
                            if ((stickerSet.set.flags & 2) == 0) {
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
                }
            }
        });
    }

    public static void loadStickers(final BaseFragment fragment, final TLRPC.InputStickerSet stickerSet) {
        if (fragment == null || stickerSet == null) {
            return;
        }

        final ProgressDialog progressDialog = new ProgressDialog(fragment.getParentActivity());
        progressDialog.setMessage(LocaleController.getString("Loading", R.string.Loading));
        progressDialog.setCanceledOnTouchOutside(false);
        progressDialog.setCancelable(false);

        TLRPC.TL_messages_getStickerSet req = new TLRPC.TL_messages_getStickerSet();
        req.stickerset = stickerSet;

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
                        if (fragment.getParentActivity() != null && !fragment.getParentActivity().isFinishing()) {
                            if (error == null) {
                                final TLRPC.TL_messages_stickerSet res = (TLRPC.TL_messages_stickerSet) response;

                                StickersAlert alert = new StickersAlert(fragment.getParentActivity(), res);
                                if (res.set == null || !StickersQuery.isStickerPackInstalled(res.set.id)) {
                                    alert.setButton(AlertDialog.BUTTON_POSITIVE, LocaleController.getString("AddStickers", R.string.AddStickers), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
                                            req.stickerset = stickerSet;
                                            ConnectionsManager.getInstance().sendRequest(req, new RequestDelegate() {
                                                @Override
                                                public void run(TLObject response, final TLRPC.TL_error error) {
                                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (fragment.getParentActivity() != null) {
                                                                if (error == null) {
                                                                    Toast.makeText(fragment.getParentActivity(), LocaleController.getString("AddStickersInstalled", R.string.AddStickersInstalled), Toast.LENGTH_SHORT).show();
                                                                } else {
                                                                    Toast.makeText(fragment.getParentActivity(), LocaleController.getString("ErrorOccurred", R.string.ErrorOccurred), Toast.LENGTH_SHORT).show();
                                                                }
                                                            }
                                                            loadStickers(false, true);
                                                        }
                                                    });
                                                }
                                            });
                                        }
                                    });
                                } else {
                                    alert.setButton(AlertDialog.BUTTON_NEUTRAL, LocaleController.getString("StickersRemove", R.string.StickersRemove), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            removeStickersSet(fragment.getParentActivity(), res.set, 0);
                                        }
                                    });
                                }
                                alert.setButton(AlertDialog.BUTTON_NEGATIVE, LocaleController.getString("Close", R.string.Close), (Message) null);
                                fragment.setVisibleDialog(alert);
                                alert.show();
                            } else {
                                Toast.makeText(fragment.getParentActivity(), LocaleController.getString("AddStickersNotFound", R.string.AddStickersNotFound), Toast.LENGTH_SHORT).show();
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
            }
        });
        fragment.setVisibleDialog(progressDialog);
        progressDialog.show();
    }

    public static void removeStickersSet(final Context context, TLRPC.StickerSet stickerSet, int hide) {
        TLRPC.TL_inputStickerSetID stickerSetID = new TLRPC.TL_inputStickerSetID();
        stickerSetID.access_hash = stickerSet.access_hash;
        stickerSetID.id = stickerSet.id;
        if (hide != 0) {
            if (hide == 1) {
                stickerSet.flags |= 2;
            } else {
                stickerSet.flags &= ~2;
            }
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
                            loadStickers(false, true);
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
