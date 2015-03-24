/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.SQLite.SQLiteCursor;
import org.telegram.SQLite.SQLitePreparedStatement;
import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessagesStorage;
import org.telegram.android.NotificationCenter;
import org.telegram.messenger.ByteBufferDesc;
import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.RPCRequest;
import org.telegram.messenger.TLClassStore;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Utilities;
import org.telegram.ui.Cells.StickerCell;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class StickersAdapter extends RecyclerView.Adapter implements NotificationCenter.NotificationCenterDelegate {

    private static boolean loadingStickers;
    private static String hash = "";
    private static int loadDate = 0;
    private static HashMap<String, ArrayList<TLRPC.Document>> allStickers;

    private Context mContext;
    private ArrayList<TLRPC.Document> stickers;
    private ArrayList<String> stickersToLoad = new ArrayList<>();
    private StickersAdapterDelegate delegate;
    private String lastSticker;
    private boolean visible;

    public interface StickersAdapterDelegate {
        void needChangePanelVisibility(boolean show);
    }

    private class Holder extends RecyclerView.ViewHolder {

        public Holder(View itemView) {
            super(itemView);
        }
    }

    public StickersAdapter(Context context, StickersAdapterDelegate delegate) {
        mContext = context;
        this.delegate = delegate;
        if (!loadingStickers && (allStickers == null || loadDate < (System.currentTimeMillis() / 1000 - 60 * 60))) {
            loadStickers(true);
        }
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
    }

    public void destroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidFailedLoad);
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.FileDidLoaded || id == NotificationCenter.FileDidFailedLoad) {
            AndroidUtilities.runOnUIThread(new Runnable() {
                @Override
                public void run() {
                    if (stickers != null && !stickers.isEmpty() && !stickersToLoad.isEmpty() && visible) {
                        String fileName = (String) args[0];
                        stickersToLoad.remove(fileName);
                        if (stickersToLoad.isEmpty()) {
                            delegate.needChangePanelVisibility(stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty());
                        }
                    }
                }
            });
        }
    }

    private void loadStickers(boolean cache) {
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
                                result = (TLRPC.messages_AllStickers) TLClassStore.Instance().TLdeserialize(data, data.readInt32());
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
            ConnectionsManager.getInstance().performRpc(req, new RPCRequest.RPCRequestDelegate() {
                @Override
                public void run(final TLObject response, final TLRPC.TL_error error) {
                    AndroidUtilities.runOnUIThread(new Runnable() {
                        @Override
                        public void run() {
                            processLoadedStickers((TLRPC.messages_AllStickers) response, false, (int)(System.currentTimeMillis() / 1000));
                        }
                    });
                }
            });
        }
    }

    private boolean checkStickerFilesExistAndDownload() {
        if (stickers == null) {
            return false;
        }
        stickersToLoad.clear();
        int size = Math.min(10, stickers.size());
        for (int a = 0; a < size; a++) {
            TLRPC.Document document = stickers.get(a);
            File f = FileLoader.getPathToAttach(document.thumb, true);
            if (!f.exists()) {
                stickersToLoad.add(FileLoader.getAttachFileName(document.thumb));
                FileLoader.getInstance().loadFile(document.thumb.location, 0, true);
            }
        }
        return stickersToLoad.isEmpty();
    }

    private void putStickersToCache(final TLRPC.TL_messages_allStickers stickers) {
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

    private void processLoadedStickers(final TLRPC.messages_AllStickers res, final boolean cache, final int date) {
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
                            hash = res.hash;
                            loadDate = date;
                            if (lastSticker != null) {
                                loadStikersForEmoji(lastSticker);
                            }
                        }
                    });
                }
            }
        });
    }

    public void loadStikersForEmoji(CharSequence emoji) {
        boolean search = emoji != null && emoji.length() != 0 && emoji.length() <= 2;
        if (search) {
            lastSticker = emoji.toString();
            if (allStickers != null) {
                ArrayList<TLRPC.Document> newStickers = allStickers.get(lastSticker);
                if (stickers != null && newStickers == null) {
                    if (visible) {
                        delegate.needChangePanelVisibility(false);
                        visible = false;
                    }
                } else {
                    stickers = newStickers;
                    checkStickerFilesExistAndDownload();
                    delegate.needChangePanelVisibility(stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty());
                    notifyDataSetChanged();
                    visible = true;
                }
            }
        }
        if (!search) {
            if (visible && stickers != null) {
                visible = false;
                delegate.needChangePanelVisibility(false);
            }
        }
    }

    public void clearStickers() {
        lastSticker = null;
        stickers = null;
        stickersToLoad.clear();
        notifyDataSetChanged();
    }

    @Override
    public int getItemCount() {
        return stickers != null ? stickers.size() : 0;
    }

    public TLRPC.Document getItem(int i) {
        return stickers != null && i >= 0 && i < stickers.size() ? stickers.get(i) : null;
    }

    @Override
    public long getItemId(int i) {
        return i;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        StickerCell view = new StickerCell(mContext);
        return new Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder viewHolder, int i) {
        Holder holder = (Holder) viewHolder;
        int side = 0;
        if (i == 0) {
            if (stickers.size() == 1) {
                side = 2;
            } else {
                side = -1;
            }
        } else if (i == stickers.size() - 1) {
            side = 1;
        }
        ((StickerCell) holder.itemView).setSticker(stickers.get(i), side);
    }
}
