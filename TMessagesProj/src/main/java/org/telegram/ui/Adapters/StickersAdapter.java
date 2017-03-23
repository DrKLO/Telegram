/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.query.StickersQuery;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.StickerCell;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;

public class StickersAdapter extends RecyclerView.Adapter implements NotificationCenter.NotificationCenterDelegate {

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
        StickersQuery.checkStickers();
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().addObserver(this, NotificationCenter.FileDidFailedLoad);
    }

    public void onDestroy() {
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance().removeObserver(this, NotificationCenter.FileDidFailedLoad);
    }

    @Override
    public void didReceivedNotification(int id, final Object... args) {
        if (id == NotificationCenter.FileDidLoaded || id == NotificationCenter.FileDidFailedLoad) {
            if (stickers != null && !stickers.isEmpty() && !stickersToLoad.isEmpty() && visible) {
                String fileName = (String) args[0];
                stickersToLoad.remove(fileName);
                if (stickersToLoad.isEmpty()) {
                    delegate.needChangePanelVisibility(stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty());
                }
            }
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
            File f = FileLoader.getPathToAttach(document.thumb, "webp", true);
            if (!f.exists()) {
                stickersToLoad.add(FileLoader.getAttachFileName(document.thumb, "webp"));
                FileLoader.getInstance().loadFile(document.thumb.location, "webp", 0, true);
            }
        }
        return stickersToLoad.isEmpty();
    }

    public void loadStikersForEmoji(CharSequence emoji) {
        boolean search = emoji != null && emoji.length() > 0 && emoji.length() <= 14;
        if (search) {
            int length = emoji.length();
            for (int a = 0; a < length; a++) {
                if (a < length - 1 && emoji.charAt(a) == 0xD83C && emoji.charAt(a + 1) >= 0xDFFB && emoji.charAt(a + 1) <= 0xDFFF) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 2, emoji.length()));
                    break;
                } else if (emoji.charAt(a) == 0xfe0f) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 1, emoji.length()));
                    length--;
                }
            }
            lastSticker = emoji.toString();
            HashMap<String, ArrayList<TLRPC.Document>> allStickers = StickersQuery.getAllStickers();
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
        ((StickerCell) viewHolder.itemView).setSticker(stickers.get(i), side);
    }
}
