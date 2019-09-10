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
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.MessageObject;
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

    private class StickerResult {
        public TLRPC.Document sticker;
        public Object parent;

        public StickerResult(TLRPC.Document s, Object p) {
            sticker = s;
            parent = p;
        }
    }

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private ArrayList<MediaDataController.KeywordResult> keywordResults;
    private ArrayList<StickerResult> stickers;
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
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_MASK);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.fileDidFailToLoad);
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.fileDidFailToLoad);
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.fileDidLoad || id == NotificationCenter.fileDidFailToLoad) {
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
            StickerResult result = stickers.get(a);
            TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(result.sticker.thumbs, 90);
            if (thumb instanceof TLRPC.TL_photoSize) {
                File f = FileLoader.getPathToAttach(thumb, "webp", true);
                if (!f.exists()) {
                    stickersToLoad.add(FileLoader.getAttachFileName(thumb, "webp"));
                    FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForDocument(thumb, result.sticker), result.parent, "webp", 1, 1);
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
            stickersMap = new HashMap<>();
        }
        stickers.add(new StickerResult(document, parent));
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
                stickersMap = new HashMap<>();
            }
            for (int b = 0, size2 = document.attributes.size(); b < size2; b++) {
                TLRPC.DocumentAttribute attribute = document.attributes.get(b);
                if (attribute instanceof TLRPC.TL_documentAttributeSticker) {
                    parent = attribute.stickerset;
                    break;
                }
            }
            stickers.add(new StickerResult(document, parent));
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
            MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
        }
        lastSearchKeyboardLanguage = newLanguage;
        String query = lastSticker;
        cancelEmojiSearch();
        searchRunnable = () -> MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, query, true, (param, alias) -> {
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
        stickersToLoad.clear();
        boolean isValidEmoji = searchEmoji && (Emoji.isValidEmoji(originalEmoji) || Emoji.isValidEmoji(lastSticker));
        if (isValidEmoji) {
            TLRPC.Document animatedSticker = MediaDataController.getInstance(currentAccount).getEmojiAnimatedSticker(emoji);
            if (animatedSticker != null) {
                ArrayList<TLRPC.TL_messages_stickerSet> sets = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_EMOJI);
                File f = FileLoader.getPathToAttach(animatedSticker, true);
                if (!f.exists()) {
                    FileLoader.getInstance(currentAccount).loadFile(ImageLocation.getForDocument(animatedSticker), sets.get(0), null, 1, 1);
                }
            }
        }
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
        stickersMap = null;
        if (lastReqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(lastReqId, true);
            lastReqId = 0;
        }

        delayLocalResults = false;
        final ArrayList<TLRPC.Document> recentStickers = MediaDataController.getInstance(currentAccount).getRecentStickersNoCopy(MediaDataController.TYPE_IMAGE);
        final ArrayList<TLRPC.Document> favsStickers = MediaDataController.getInstance(currentAccount).getRecentStickersNoCopy(MediaDataController.TYPE_FAVE);
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

        HashMap<String, ArrayList<TLRPC.Document>> allStickers = MediaDataController.getInstance(currentAccount).getAllStickers();
        ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(lastSticker) : null;
        if (newStickers != null && !newStickers.isEmpty()) {
            addStickersToResult(newStickers, null);
        }
        if (stickers != null) {
            Collections.sort(stickers, new Comparator<StickerResult>() {
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
                public int compare(StickerResult lhs, StickerResult rhs) {
                    boolean isAnimated1 = MessageObject.isAnimatedStickerDocument(lhs.sticker);
                    boolean isAnimated2 = MessageObject.isAnimatedStickerDocument(rhs.sticker);
                    if (isAnimated1 == isAnimated2) {
                        int idx1 = getIndex(lhs.sticker.id);
                        int idx2 = getIndex(rhs.sticker.id);
                        if (idx1 > idx2) {
                            return -1;
                        } else if (idx1 < idx2) {
                            return 1;
                        }
                        return 0;
                    } else {
                        if (isAnimated1 && !isAnimated2) {
                            return -1;
                        } else {
                            return 1;
                        }
                    }
                }
            });
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
                boolean show = stickersToLoad.isEmpty();
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
                boolean show = stickersToLoad.isEmpty();
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
        if (stickersToLoad.isEmpty()) {
            lastSticker = null;
            stickers = null;
            stickersMap = null;
        }
        keywordResults = null;
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
        return stickers != null && i >= 0 && i < stickers.size() ? stickers.get(i).sticker : null;
    }

    public Object getItemParent(int i) {
        if (keywordResults != null && !keywordResults.isEmpty()) {
            return null;
        }
        return stickers != null && i >= 0 && i < stickers.size() ? stickers.get(i).parent : null;
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
                StickerResult result = stickers.get(position);
                stickerCell.setSticker(result.sticker, result.parent, side);
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
