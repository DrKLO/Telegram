/*
 * This is the source code of Telegram for Android v. 2.x
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2015.
 */

package org.telegram.android.query;

import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Message;
import android.widget.Toast;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.LocaleController;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.StickersAlert;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class StickersQuery {

    private static String hash;
    private static int loadDate;
    private static ArrayList<TLRPC.Document> stickers = new ArrayList<>();
    private static HashMap<String, ArrayList<TLRPC.Document>> allStickers = new HashMap<>();
    private static ArrayList<TLRPC.TL_stickerPack> stickerPacks = new ArrayList<>();
    private static ArrayList<TLRPC.TL_stickerSet> stickerSets = new ArrayList<>();
    private static HashMap<Long, ArrayList<TLRPC.Document>> stickersBySets = new HashMap<>();
    private static HashMap<Long, String> stickersByEmoji = new HashMap<>();
    private static boolean loadingStickers;
    private static boolean stickersLoaded;
    private static boolean hideMainStickersPack;

    public static void checkStickers() {
        if (!loadingStickers && (!stickersLoaded || loadDate < (System.currentTimeMillis() / 1000 - 60 * 60))) {
            loadStickers(true, false);
        }
    }

    public static boolean isLoadingStickers() {
        return loadingStickers;
    }

    public static HashMap<String, ArrayList<TLRPC.Document>> getAllStickers() {
        return allStickers;
    }

    public static ArrayList<TLRPC.Document> getStickersForSet(long id) {
        return stickersBySets.get(id);
    }

    public static ArrayList<TLRPC.TL_stickerPack> getStickerPacks() {
        return stickerPacks;
    }

    public static ArrayList<TLRPC.Document> getStickers() {
        return stickers;
    }

    public static ArrayList<TLRPC.TL_stickerSet> getStickerSets() {
        return stickerSets;
    }

    public static boolean isStickerPackInstalled(long id) {
        return stickersBySets.containsKey(id);
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
                    TLRPC.messages_AllStickers result = null;
                    int date = 0;
                    try {
                        SQLiteCursor cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT value FROM keyvalue WHERE id = 'hide_stickers'");
                        if (cursor.next()) {
                            int value = Utilities.parseInt(cursor.stringValue(0));
                            hideMainStickersPack = value == 1;
                        }
                        cursor.dispose();

                        cursor = MessagesStorage.getInstance().getDatabase().queryFinalized("SELECT data, date FROM stickers WHERE 1");
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
            req.hash = hash == null || force ? "" : hash;
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

    private static long getStickerSetId(TLRPC.Document document) {
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

    private static void processLoadedStickers(final TLRPC.messages_AllStickers res, final boolean cache, final int date) {
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
                if ((res == null || date < (int) (System.currentTimeMillis() / 1000 - 60 * 60)) && cache) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            loadStickers(false, false);
                        }
                    });
                    if (res == null) {
                        return;
                    }
                }
                if (res instanceof TLRPC.TL_messages_allStickers) {
                    HashMap<Long, TLRPC.Document> documents = new HashMap<>();
                    final HashMap<Long, ArrayList<TLRPC.Document>> sets = new HashMap<>();
                    final ArrayList<TLRPC.Document> allDocuments = new ArrayList<>();
                    final HashMap<Long, String> stickersEmoji = new HashMap<>();
                    for (TLRPC.Document document : res.documents) {
                        if (document == null) {
                            continue;
                        }

                        documents.put(document.id, document);
                        long setId = getStickerSetId(document);
                        if (setId != -1 || setId == -1 && !hideMainStickersPack) {
                            allDocuments.add(document);
                        }
                        ArrayList<TLRPC.Document> docs = sets.get(setId);
                        if (docs == null) {
                            docs = new ArrayList<>();
                            sets.put(setId, docs);
                            if (setId == -1) {
                                boolean contain = false;
                                for (TLRPC.TL_stickerSet set : res.sets) {
                                    if (set.id == setId) {
                                        contain = true;
                                        break;
                                    }
                                }
                                if (!contain) {
                                    TLRPC.TL_stickerSet set = new TLRPC.TL_stickerSet();
                                    set.title = set.short_name = "";
                                    set.id = -1;
                                    res.sets.add(0, set);
                                }
                            }
                        }
                        docs.add(document);
                    }
                    final HashMap<String, ArrayList<TLRPC.Document>> result = new HashMap<>();
                    for (TLRPC.TL_stickerPack stickerPack : res.packs) {
                        if (stickerPack != null && stickerPack.emoticon != null) {
                            stickerPack.emoticon = stickerPack.emoticon.replace("\uFE0F", "");
                            ArrayList<TLRPC.Document> arrayList = result.get(stickerPack.emoticon);
                            for (Long id : stickerPack.documents) {
                                if (!stickersEmoji.containsKey(id)) {
                                    stickersEmoji.put(id, stickerPack.emoticon);
                                }
                                TLRPC.Document document = documents.get(id);
                                if (document != null) {
                                    long setId = getStickerSetId(document);
                                    if (setId == -1 && hideMainStickersPack) {
                                        continue;
                                    }

                                    if (arrayList == null) {
                                        arrayList = new ArrayList<>();
                                        result.put(stickerPack.emoticon, arrayList);
                                    }
                                    arrayList.add(document);
                                }
                            }
                        }
                    }
                    Collections.sort(allDocuments, new Comparator<TLRPC.Document>() {
                        @Override
                        public int compare(TLRPC.Document lhs, TLRPC.Document rhs) {
                            long lid = getStickerSetId(lhs);
                            long rid = getStickerSetId(rhs);
                            if (lid < rid) {
                                return -1;
                            } else if (lid > rid) {
                                return 1;
                            }
                            return 0;
                        }
                    });
                    if (!cache) {
                        putStickersToCache((TLRPC.TL_messages_allStickers) res);
                    }
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            stickerSets = res.sets;
                            allStickers = result;
                            stickers = allDocuments;
                            stickersBySets = sets;
                            stickersByEmoji = stickersEmoji;
                            hash = res.hash;
                            loadDate = date;
                            NotificationCenter.getInstance().postNotificationName(NotificationCenter.stickersDidLoaded);
                        }
                    });
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

        final long reqId = ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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
                        if (fragment != null && fragment.getParentActivity() != null && !fragment.getParentActivity().isFinishing()) {
                            if (error == null) {
                                final TLRPC.TL_messages_stickerSet res = (TLRPC.TL_messages_stickerSet) response;

                                StickersAlert alert = new StickersAlert(fragment.getParentActivity(), res.set, res.documents);
                                if (res.set == null || !StickersQuery.isStickerPackInstalled(res.set.id)) {
                                    alert.setButton(AlertDialog.BUTTON_POSITIVE, LocaleController.getString("AddStickers", R.string.AddStickers), new DialogInterface.OnClickListener() {
                                        @Override
                                        public void onClick(DialogInterface dialog, int which) {
                                            TLRPC.TL_messages_installStickerSet req = new TLRPC.TL_messages_installStickerSet();
                                            req.stickerset = stickerSet;
                                            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                                                @Override
                                                public void run(TLObject response, final TLRPC.TL_error error) {
                                                    AndroidUtilities.runOnUIThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            if (fragment != null && fragment.getParentActivity() != null) {
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
                                            removeStickersSet(fragment.getParentActivity(), res.set);
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
                ConnectionsManager.getInstance().cancelRpc(reqId, true);
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

    public static void setHideMainStickersPack(final boolean value) {
        hideMainStickersPack = value;
        MessagesStorage.getInstance().getStorageQueue().postRunnable(new Runnable() {
            @Override
            public void run() {
                try {
                    SQLitePreparedStatement state = MessagesStorage.getInstance().getDatabase().executeFast("REPLACE INTO keyvalue VALUES(?, ?)");
                    state.requery();
                    state.bindString(1, "hide_stickers");
                    state.bindString(2, value ? "1" : "0");
                    state.step();
                    state.dispose();
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
            }
        });
    }

    public static void removeStickersSet(final Context context, TLRPC.TL_stickerSet stickerSet) {
        TLRPC.TL_messages_uninstallStickerSet req = new TLRPC.TL_messages_uninstallStickerSet();
        req.stickerset = new TLRPC.TL_inputStickerSetID();
        req.stickerset.access_hash = stickerSet.access_hash;
        req.stickerset.id = stickerSet.id;
        ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
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

    public static boolean getHideMainStickersPack() {
        return hideMainStickersPack;
    }
}
