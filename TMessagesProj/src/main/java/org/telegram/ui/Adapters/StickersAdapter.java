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
import android.view.ViewGroup;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.FileLoader;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.EmojiReplacementCell;
import org.telegram.ui.Components.RecyclerListView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;

import androidx.recyclerview.widget.RecyclerView;

public class StickersAdapter extends RecyclerListView.SelectionAdapter implements NotificationCenter.NotificationCenterDelegate {

    private int currentAccount = UserConfig.selectedAccount;
    private Context mContext;
    private ArrayList<MediaDataController.KeywordResult> keywordResults;
    private StickersAdapterDelegate delegate;

    private boolean visible;

    private String lastSearch;

    private String[] lastSearchKeyboardLanguage;
    private Runnable searchRunnable;
    private final Theme.ResourcesProvider resourcesProvider;

    public interface StickersAdapterDelegate {
        void needChangePanelVisibility(boolean show);
    }

    public StickersAdapter(Context context, StickersAdapterDelegate delegate, Theme.ResourcesProvider resourcesProvider) {
        mContext = context;
        this.delegate = delegate;
        this.resourcesProvider = resourcesProvider;
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_MASK);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
    }

    public void onDestroy() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.newEmojiSuggestionsAvailable);
    }

    @Override
    public void didReceivedNotification(int id, int account, final Object... args) {
        if (id == NotificationCenter.newEmojiSuggestionsAvailable) {
            if ((keywordResults == null || keywordResults.isEmpty()) && !TextUtils.isEmpty(lastSearch) && getItemCount() == 0) {
                searchEmojiByKeyword();
            }
        }
    }

    public void hide() {
        if (visible && keywordResults != null && !keywordResults.isEmpty()) {
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
        String query = lastSearch;
        cancelEmojiSearch();
        searchRunnable = () -> MediaDataController.getInstance(currentAccount).getEmojiSuggestions(lastSearchKeyboardLanguage, query, true, (param, alias) -> {
            if (query.equals(lastSearch)) {
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

    public void searchEmojiByKeyword(CharSequence emoji) {
        boolean searchEmoji = emoji != null && emoji.length() > 0 && emoji.length() <= 14;

        String originalEmoji = "";
        if (searchEmoji) {
            originalEmoji = emoji.toString();
            int length = emoji.length();
            for (int a = 0; a < length; a++) {
                char ch = emoji.charAt(a);
                char nch = a < length - 1 ? emoji.charAt(a + 1) : 0;
                if (a < length - 1 && ch == 0xD83C && nch >= 0xDFFB && nch <= 0xDFFF) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 2, emoji.length()));
                    length -= 2;
                    a--;
                } else if (ch == 0xfe0f) {
                    emoji = TextUtils.concat(emoji.subSequence(0, a), emoji.subSequence(a + 1, emoji.length()));
                    length--;
                    a--;
                }
            }
        }
        lastSearch = emoji.toString().trim();
        boolean isValidEmoji = searchEmoji && (Emoji.isValidEmoji(originalEmoji) || Emoji.isValidEmoji(lastSearch));
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

        if (visible && (keywordResults == null || keywordResults.isEmpty())) {
            visible = false;
            delegate.needChangePanelVisibility(false);
            notifyDataSetChanged();
        }
        if (!isValidEmoji) {
            searchEmojiByKeyword();
        } else {
            clearSearch();
            delegate.needChangePanelVisibility(false);
        }
    }

    public void clearSearch() {
        lastSearch = null;
        keywordResults = null;
        notifyDataSetChanged();
    }

    public String getQuery() {
        return lastSearch;
    }

    public boolean isShowingKeywords() {
        return keywordResults != null && !keywordResults.isEmpty();
    }

    @Override
    public int getItemCount() {
        if (keywordResults != null && !keywordResults.isEmpty()) {
            return keywordResults.size();
        }
        return 0;
    }

    public Object getItem(int i) {
        if (keywordResults != null && !keywordResults.isEmpty()) {
            return i >= 0 && i < keywordResults.size() ? keywordResults.get(i).emoji : null;
        }
        return null;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup viewGroup, int viewType) {
        return new RecyclerListView.Holder(new EmojiReplacementCell(mContext, resourcesProvider));
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
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
    }
}
