/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.support.widget.RecyclerView;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

public class StickersAdapter extends RecyclerListView.SelectionAdapter implements NotificationCenter.NotificationCenterDelegate {

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private ArrayList<TLRPC.Document> stickers;
    private HashMap<String, TLRPC.Document> stickersMap;
    private ArrayList<String> stickersToLoad = new ArrayList<>();
    private StickersAdapterDelegate delegate;
    private String lastSticker;
    private boolean visible;
    private int lastReqId;
    private boolean delayLocalResults;

    public interface StickersAdapterDelegate {
        void needChangePanelVisibility(boolean show);
    }

    public StickersAdapter(Context context, StickersAdapterDelegate delegate) {
        mContext = context;
        this.delegate = delegate;
        DataQuery.getInstance(currentAccount).checkStickers(DataQuery.TYPE_IMAGE);
        DataQuery.getInstance(currentAccount).checkStickers(DataQuery.TYPE_MASK);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.FileDidFailedLoad);
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidLoaded);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.FileDidFailedLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
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
                FileLoader.getInstance(currentAccount).loadFile(document.thumb.location, "webp", 0, 1);
            }
        }
        return stickersToLoad.isEmpty();
    }

    private boolean isValidSticker(TLRPC.Document document, String emoji) {
        for (int b = 0, size2 = document.attributes.size(); b < size2; b++) {
            TLRPC.DocumentAttribute attribute = document.attributes.get(b);
            if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                if (attribute.alt != null && attribute.alt.contains(emoji)) {
                    return true;
                }
                break;
            }
        }
        return false;
    }

    private void addStickerToResult(TLRPC.Document document) {
        if (document == null) {
            return;
        }
        String key = document.dc_id + "_" + document.id;
        if (stickersMap != null && stickersMap.containsKey(key)) {
            return;
        }
        if (stickers == null) {
            stickers = new ArrayList<>();
            stickersMap = new HashMap<>();
        }
        stickers.add(document);
        stickersMap.put(key, document);
    }

    private void addStickersToResult(ArrayList<TLRPC.Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        for (int a = 0, size = documents.size(); a < size; a++) {
            TLRPC.Document document = documents.get(a);
            String key = document.dc_id + "_" + document.id;
            if (stickersMap != null && stickersMap.containsKey(key)) {
                continue;
            }
            if (stickers == null) {
                stickers = new ArrayList<>();
                stickersMap = new HashMap<>();
            }
            stickers.add(document);
            stickersMap.put(key, document);
        }
    }

    public void loadStikersForEmoji(CharSequence emoji) {
        if (SharedConfig.suggestStickers == 2) {
            return;
        }
        boolean search = emoji != null && emoji.length() > 0 && emoji.length() <= 14;
        if (search) {
            String originalEmoji = emoji.toString();
            int length = emoji.length();
            for (int a = 0; a < length; a++) {
                if (a < length - 1 && (emoji.charAt(a) == 0xD83C && emoji.charAt(a + 1) >= 0xDFFB && emoji.charAt(a + 1) <= 0xDFFF || emoji.charAt(a) == 0x200D && (emoji.charAt(a + 1) == 0x2640 || emoji.charAt(a + 1) == 0x2642))) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 2, emoji.length()));
                    length -= 2;
                    a--;
                } else if (emoji.charAt(a) == 0xfe0f) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 1, emoji.length()));
                    length--;
                    a--;
                }
            }
            lastSticker = emoji.toString().trim();
            if (!Emoji.isValidEmoji(originalEmoji) && !Emoji.isValidEmoji(lastSticker)) {
                if (visible) {
                    visible = false;
                    delegate.needChangePanelVisibility(false);
                    notifyDataSetChanged();
                }
                return;
            }
            stickers = null;
            stickersMap = null;

            delayLocalResults = false;
            final ArrayList<TLRPC.Document> recentStickers = DataQuery.getInstance(currentAccount).getRecentStickersNoCopy(DataQuery.TYPE_IMAGE);
            final ArrayList<TLRPC.Document> favsStickers = DataQuery.getInstance(currentAccount).getRecentStickersNoCopy(DataQuery.TYPE_FAVE);
            int recentsAdded = 0;
            for (int a = 0, size = recentStickers.size(); a < size; a++) {
                TLRPC.Document document = recentStickers.get(a);
                if (isValidSticker(document, lastSticker)) {
                    addStickerToResult(document);
                    recentsAdded++;
                    if (recentsAdded >= 5) {
                        break;
                    }
                }
            }
            for (int a = 0, size = favsStickers.size(); a < size; a++) {
                TLRPC.Document document = favsStickers.get(a);
                if (isValidSticker(document, lastSticker)) {
                    addStickerToResult(document);
                }
            }

            HashMap<String, ArrayList<TLRPC.Document>> allStickers = DataQuery.getInstance(currentAccount).getAllStickers();
            ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(lastSticker) : null;
            if (newStickers != null && !newStickers.isEmpty()) {
                ArrayList<TLRPC.Document> arrayList = new ArrayList<>(newStickers);
                if (!recentStickers.isEmpty()) {
                    Collections.sort(arrayList, new Comparator<TLRPC.Document>() {
                        private int getIndex(long id) {
                            for (int a = 0; a < favsStickers.size(); a++) {
                                if (favsStickers.get(a).id == id) {
                                    return a + 1000;
                                }
                            }
                            for (int a = 0; a < recentStickers.size(); a++) {
                                if (recentStickers.get(a).id == id) {
                                    return a;
                                }
                            }
                            return -1;
                        }

                        @Override
                        public int compare(TLRPC.Document lhs, TLRPC.Document rhs) {
                            int idx1 = getIndex(lhs.id);
                            int idx2 = getIndex(rhs.id);
                            if (idx1 > idx2) {
                                return -1;
                            } else if (idx1 < idx2) {
                                return 1;
                            }
                            return 0;
                        }
                    });
                }
                addStickersToResult(arrayList);
            }
            if (SharedConfig.suggestStickers == 0) {
                searchServerStickers(lastSticker);
            }

            if (stickers != null && !stickers.isEmpty()) {
                if (SharedConfig.suggestStickers == 0 && stickers.size() < 5) {
                    delayLocalResults = true;
                    delegate.needChangePanelVisibility(false);
                    visible = false;
                } else {
                    checkStickerFilesExistAndDownload();
                    delegate.needChangePanelVisibility(stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty());
                    visible = true;
                }
                notifyDataSetChanged();
            } else if (visible) {
                delegate.needChangePanelVisibility(false);
                visible = false;
            }
        } else {
            lastSticker = "";
            if (visible && stickers != null) {
                visible = false;
                delegate.needChangePanelVisibility(false);
            }
        }
    }

    private void searchServerStickers(final String emoji) {
        if (lastReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true);
        }
        TLRPC.TL_messages_getStickers req = new TLRPC.TL_messages_getStickers();
        req.emoticon = emoji;
        req.hash = 0;
        lastReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            lastReqId = 0;
            if (!emoji.equals(lastSticker) || !(response instanceof TLRPC.TL_messages_stickers)) {
                return;
            }
            delayLocalResults = false;
            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
            int oldCount = stickers != null ? stickers.size() : 0;
            addStickersToResult(res.stickers);
            int newCount = stickers != null ? stickers.size() : 0;
            if (!visible && stickers != null && !stickers.isEmpty()) {
                checkStickerFilesExistAndDownload();
                delegate.needChangePanelVisibility(stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty());
                visible = true;
            }
            if (oldCount != newCount) {
                notifyDataSetChanged();
            }
        }));
    }

    public void clearStickers() {
        lastSticker = null;
        stickers = null;
        stickersMap = null;
        stickersToLoad.clear();
        notifyDataSetChanged();
        if (lastReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true);
            lastReqId = 0;
        }
    }

    @Override
    public int getItemCount() {
        return !delayLocalResults && stickers != null ? stickers.size() : 0;
    }

    public TLRPC.Document getItem(int i) {
        return stickers != null && i >= 0 && i < stickers.size() ? stickers.get(i) : null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return true;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int i) {
        StickerCell view = new StickerCell(mContext);
        return new RecyclerListView.Holder(view);
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
