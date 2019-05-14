/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Adapters;

import android.content.Context;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DataQuery;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Cells.EmojiReplacementCell;
import org.telegram.ui.Cells.StickerCell;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import androidx.recyclerview.widget.RecyclerView;

public class StickersAdapter extends RecyclerListView.SelectionAdapter implements NotificationCenter.NotificationCenterDelegate {

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private ArrayList<DataQuery.KeywordResult> keywordResults;
    private ArrayList<TLRPC.Document> stickers;
    private ArrayList<Object> stickersParents;
    private HashMap<String, TLRPC.Document> stickersMap;
    private ArrayList<String> stickersToLoad = new ArrayList<>();
    private StickersAdapterDelegate delegate;
    private String lastSticker;
    private boolean visible;
    private int lastReqId;
    private boolean delayLocalResults;
    private String[] lastSearchKeyboardLanguage;
    private Runnable searchRunnable;

    public interface StickersAdapterDelegate {
        void needChangePanelVisibility(boolean show);
    }

    public StickersAdapter(Context context, StickersAdapterDelegate delegate) {
        mContext = context;
        this.delegate = delegate;
        DataQuery.getInstance(currentAccount).checkStickers(DataQuery.TYPE_IMAGE);
        DataQuery.getInstance(currentAccount).checkStickers(DataQuery.TYPE_MASK);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidFailedLoad);
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidFailedLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.fileDidLoad || id == NotificationCenter.fileDidFailedLoad) {
            if (stickers != null && !stickers.isEmpty() && !stickersToLoad.isEmpty() && visible) {
                String fileName = (String) args[0];
                stickersToLoad.remove(fileName);
                if (stickersToLoad.isEmpty()) {
                    boolean show = stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty();
                    if (show) {
                        keywordResults = null;
                    }
                    delegate.needChangePanelVisibility(show);
                }
            }
        } else if (id == NotificationCenter.newEmojiSuggestionsAvailable) {
            if ((keywordResults == null || keywordResults.isEmpty()) && !TextUtils.isEmpty(lastSticker) && getItemCount() == 0) {
                searchEmojiByKeyword();
            }
        }
    }

    private boolean checkStickerFilesExistAndDownload() {
        if (stickers == null) {
            return false;
        }
        stickersToLoad.clear();
        int size = Math.min(6, stickers.size());
        for (int a = 0; a < size; a++) {
            TLRPC.Document document = stickers.get(a);
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
            if (thumb instanceof TLRPC.TL_photoSize) {
                File f = FileLoader.getPathToAttach(thumb, "webp", true);
                if (!f.exists()) {
                    stickersToLoad.add(FileLoader.getAttachFileName(thumb, "webp"));
                    FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForDocument(thumb, document), stickersParents.get(a), "webp", 1, 1);
                }
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

    private void addStickerToResult(TLRPC.Document document, Object parent) {
        if (document == null) {
            return;
        }
        String key = document.dc_id + "_" + document.id;
        if (stickersMap != null && stickersMap.containsKey(key)) {
            return;
        }
        if (stickers == null) {
            stickers = new ArrayList<>();
            stickersParents = new ArrayList<>();
            stickersMap = new HashMap<>();
        }
        stickers.add(document);
        stickersParents.add(parent);
        stickersMap.put(key, document);
    }

    private void addStickersToResult(ArrayList<TLRPC.Document> documents, Object parent) {
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
                stickersParents = new ArrayList<>();
                stickersMap = new HashMap<>();
            }
            stickers.add(document);
            boolean found = false;
            for (int b = 0, size2 = document.attributes.size(); b < size2; b++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(b);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    stickersParents.add(attribute.stickerset);
                    found = true;
                    break;
                }
            }
            if (!found) {
                stickersParents.add(parent);
            }
            stickersMap.put(key, document);
        }
    }

    public void hide() {
        if (visible && (stickers != null || keywordResults != null && !keywordResults.isEmpty())) {
            visible = false;
            delegate.needChangePanelVisibility(false);
        }
    }

    private void cancelEmojiSearch() {
        if (searchRunnable != null) {
            AndroidUtilities.cancelRunOnUIThread(searchRunnable);
            searchRunnable = null;
        }
    }

    private void searchEmojiByKeyword() {
        String[] newLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
        if (!Arrays.equals(newLanguage, lastSearchKeyboardLanguage)) {
            DataQuery.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
        }
        lastSearchKeyboardLanguage = newLanguage;
        String query = lastSticker;
        cancelEmojiSearch();
        searchRunnable = () -> DataQuery.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, query, true, (param, alias) -> {
            if (query.equals(lastSticker)) {
                if (!param.isEmpty()) {
                    keywordResults = param;
                }
                notifyDataSetChanged();
                delegate.needChangePanelVisibility(visible = !param.isEmpty());
            }
        });
        if (keywordResults == null || keywordResults.isEmpty()) {
            AndroidUtilities.runOnUIThread(searchRunnable, 1000);
        } else {
            searchRunnable.run();
        }
    }

    public void loadStikersForEmoji(CharSequence emoji, boolean emojiOnly) {
        boolean searchEmoji = emoji != null && emoji.length() > 0 && emoji.length() <= 14;

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
        lastSticker = emoji.toString();
        boolean isValidEmoji = searchEmoji && (Emoji.isValidEmoji(originalEmoji) || Emoji.isValidEmoji(lastSticker));
        if (emojiOnly || SharedConfig.suggestStickers == 2 || !isValidEmoji) {
            if (visible && (keywordResults == null || keywordResults.isEmpty())) {
                visible = false;
                delegate.needChangePanelVisibility(false);
                notifyDataSetChanged();
            }
            if (!isValidEmoji) {
                searchEmojiByKeyword();
            }
            return;
        }
        cancelEmojiSearch();
        stickers = null;
        stickersParents = null;
        stickersMap = null;
        if (lastReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true);
            lastReqId = 0;
        }

        delayLocalResults = false;
        final ArrayList<TLRPC.Document> recentStickers = DataQuery.getInstance(currentAccount).getRecentStickersNoCopy(DataQuery.TYPE_IMAGE);
        final ArrayList<TLRPC.Document> favsStickers = DataQuery.getInstance(currentAccount).getRecentStickersNoCopy(DataQuery.TYPE_FAVE);
        int recentsAdded = 0;
        for (int a = 0, size = recentStickers.size(); a < size; a++) {
            TLRPC.Document document = recentStickers.get(a);
            if (isValidSticker(document, lastSticker)) {
                addStickerToResult(document, "recent");
                recentsAdded++;
                if (recentsAdded >= 5) {
                    break;
                }
            }
        }
        for (int a = 0, size = favsStickers.size(); a < size; a++) {
            TLRPC.Document document = favsStickers.get(a);
            if (isValidSticker(document, lastSticker)) {
                addStickerToResult(document, "fav");
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

            addStickersToResult(arrayList, null);
        }
        if (SharedConfig.suggestStickers == 0) {
            searchServerStickers(lastSticker, originalEmoji);
        }

        if (stickers != null && !stickers.isEmpty()) {
            if (SharedConfig.suggestStickers == 0 && stickers.size() < 5) {
                delayLocalResults = true;
                delegate.needChangePanelVisibility(false);
                visible = false;
            } else {
                checkStickerFilesExistAndDownload();
                boolean show = stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty();
                if (show) {
                    keywordResults = null;
                }
                delegate.needChangePanelVisibility(show);
                visible = true;
            }
            notifyDataSetChanged();
        } else if (visible) {
            delegate.needChangePanelVisibility(false);
            visible = false;
        }
    }

    private void searchServerStickers(final String emoji, final String originalEmoji) {
        TLRPC.TL_messages_getStickers req = new TLRPC.TL_messages_getStickers();
        req.emoticon = originalEmoji;
        req.hash = 0;
        lastReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            lastReqId = 0;
            if (!emoji.equals(lastSticker) || !(response instanceof TLRPC.TL_messages_stickers)) {
                return;
            }
            delayLocalResults = false;
            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
            int oldCount = stickers != null ? stickers.size() : 0;
            addStickersToResult(res.stickers, "sticker_search_" + emoji);
            int newCount = stickers != null ? stickers.size() : 0;
            if (!visible && stickers != null && !stickers.isEmpty()) {
                checkStickerFilesExistAndDownload();
                boolean show = stickers != null && !stickers.isEmpty() && stickersToLoad.isEmpty();
                if (show) {
                    keywordResults = null;
                }
                delegate.needChangePanelVisibility(show);
                visible = true;
            }
            if (oldCount != newCount) {
                notifyDataSetChanged();
            }
        }));
    }

    public void clearStickers() {
        if (delayLocalResults || lastReqId != 0) {
            return;
        }
        lastSticker = null;
        stickers = null;
        stickersParents = null;
        stickersMap = null;
        keywordResults = null;
        stickersToLoad.clear();
        notifyDataSetChanged();
        if (lastReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true);
            lastReqId = 0;
        }
    }

    public boolean isShowingKeywords() {
        return keywordResults != null && !keywordResults.isEmpty();
    }

    @Override
    public int getItemCount() {
        if (keywordResults != null && !keywordResults.isEmpty()) {
            return keywordResults.size();
        }
        return !delayLocalResults && stickers != null ? stickers.size() : 0;
    }

    public Object getItem(int i) {
        if (keywordResults != null && !keywordResults.isEmpty()) {
            return keywordResults.get(i).emoji;
        }
        return stickers != null && i >= 0 && i < stickers.size() ? stickers.get(i) : null;
    }

    public Object getItemParent(int i) {
        if (keywordResults != null && !keywordResults.isEmpty()) {
            return null;
        }
        return stickersParents != null && i >= 0 && i < stickersParents.size() ? stickersParents.get(i) : null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        View view;
        switch (viewType) {
            case 0:
                view = new StickerCell(mContext);
                break;
            case 1:
            default:
                view = new EmojiReplacementCell(mContext);
        }
        return new RecyclerListView.Holder(view);
    }

    @Override
    public int getItemViewType(int position) {
        if (keywordResults != null && !keywordResults.isEmpty()) {
            return 1;
        }
        return 0;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                int side = 0;
                if (position == 0) {
                    if (stickers.size() == 1) {
                        side = 2;
                    } else {
                        side = -1;
                    }
                } else if (position == stickers.size() - 1) {
                    side = 1;
                }
                StickerCell stickerCell = (StickerCell) holder.itemView;
                stickerCell.setSticker(stickers.get(position), stickersParents.get(position), side);
                stickerCell.setClearsInputField(true);
                break;
            }
            case 1: {
                int side = 0;
                if (position == 0) {
                    if (keywordResults.size() == 1) {
                        side = 2;
                    } else {
                        side = -1;
                    }
                } else if (position == keywordResults.size() - 1) {
                    side = 1;
                }
                EmojiReplacementCell cell = (EmojiReplacementCell) holder.itemView;
                cell.setEmoji(keywordResults.get(position).emoji, side);
                break;
            }
        }
    }
}
