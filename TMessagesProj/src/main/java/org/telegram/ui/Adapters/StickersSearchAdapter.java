package org.telegram.ui.Adapters;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.text.TextUtils;
import android.util.LongSparseArray;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.ActionBar.ThemeDescription;
import org.telegram.ui.Cells.EmptyCell;
import org.telegram.ui.Cells.FeaturedStickerSetInfoCell;
import org.telegram.ui.Cells.StickerEmojiCell;
import org.telegram.ui.Cells.StickerSetNameCell;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class StickersSearchAdapter extends RecyclerListView.SelectionAdapter {

    public static final int PAYLOAD_ANIMATED = 0;

    public interface Delegate {

        void onSearchStart();

        void onSearchStop();

        void setAdapterVisible(boolean visible);

        void onStickerSetAdd(TLRPC.StickerSetCovered stickerSet, boolean primary);

        void onStickerSetRemove(TLRPC.StickerSetCovered stickerSet);

        int getStickersPerRow();

        String[] getLastSearchKeyboardLanguage();

        void setLastSearchKeyboardLanguage(String[] language);
    }

    private final int currentAccount = UserConfig.selectedAccount;

    private final Context context;
    private final Delegate delegate;
    private final TLRPC.StickerSetCovered[] primaryInstallingStickerSets;
    private final LongSparseArray<TLRPC.StickerSetCovered> installingStickerSets;
    private final LongSparseArray<TLRPC.StickerSetCovered> removingStickerSets;

    private SparseArray<Object> rowStartPack = new SparseArray<>();
    private SparseArray<Object> cache = new SparseArray<>();
    private SparseArray<Object> cacheParent = new SparseArray<>();
    private SparseIntArray positionToRow = new SparseIntArray();
    private SparseArray<String> positionToEmoji = new SparseArray<>();
    private int totalItems;

    private ArrayList<TLRPC.StickerSetCovered> serverPacks = new ArrayList<>();
    private ArrayList<TLRPC.TL_messages_stickerSet> localPacks = new ArrayList<>();
    private HashMap<TLRPC.TL_messages_stickerSet, Boolean> localPacksByShortName = new HashMap<>();
    private HashMap<TLRPC.TL_messages_stickerSet, Integer> localPacksByName = new HashMap<>();
    private HashMap<ArrayList<TLRPC.Document>, String> emojiStickers = new HashMap<>();
    private ArrayList<ArrayList<TLRPC.Document>> emojiArrays = new ArrayList<>();
    private SparseArray<TLRPC.StickerSetCovered> positionsToSets = new SparseArray<>();

    private ImageView emptyImageView;
    private TextView emptyTextView;

    private int reqId;
    private int reqId2;

    private int emojiSearchId;
    boolean cleared;
    private String searchQuery;
    private Runnable searchRunnable = new Runnable() {

        private void clear() {
            if (cleared) {
                return;
            }
            cleared = true;
            emojiStickers.clear();
            emojiArrays.clear();
            localPacks.clear();
            serverPacks.clear();
            localPacksByShortName.clear();
            localPacksByName.clear();
        }

        @Override
        public void run() {
            if (TextUtils.isEmpty(searchQuery)) {
                return;
            }
            delegate.onSearchStart();
            cleared = false;
            int lastId = ++emojiSearchId;

            final ArrayList<TLRPC.Document> emojiStickersArray = new ArrayList<>(0);
            final LongSparseArray<TLRPC.Document> emojiStickersMap = new LongSparseArray<>(0);
            HashMap<String, ArrayList<TLRPC.Document>> allStickers = MediaDataController.getInstance(currentAccount).getAllStickers();
            if (searchQuery.length() <= 14) {
                CharSequence emoji = searchQuery;
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
                ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(emoji.toString()) : null;
                if (newStickers != null && !newStickers.isEmpty()) {
                    clear();
                    emojiStickersArray.addAll(newStickers);
                    for (int a = 0, size = newStickers.size(); a < size; a++) {
                        TLRPC.Document document = newStickers.get(a);
                        emojiStickersMap.put(document.id, document);
                    }
                    emojiStickers.put(emojiStickersArray, searchQuery);
                    emojiArrays.add(emojiStickersArray);
                }
            }
            if (allStickers != null && !allStickers.isEmpty() && searchQuery.length() > 1) {
                String[] newLanguage = AndroidUtilities.getCurrentKeyboardLanguage();
                if (!Arrays.equals(delegate.getLastSearchKeyboardLanguage(), newLanguage)) {
                    MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(newLanguage);
                }
                delegate.setLastSearchKeyboardLanguage(newLanguage);
                MediaDataController.getInstance(currentAccount).getEmojiSuggestions(delegate.getLastSearchKeyboardLanguage(), searchQuery, false, (param, alias) -> {
                    if (lastId != emojiSearchId) {
                        return;
                    }
                    boolean added = false;
                    for (int a = 0, size = param.size(); a < size; a++) {
                        String emoji = param.get(a).emoji;
                        ArrayList<TLRPC.Document> newStickers = allStickers != null ? allStickers.get(emoji) : null;
                        if (newStickers != null && !newStickers.isEmpty()) {
                            clear();
                            if (!emojiStickers.containsKey(newStickers)) {
                                emojiStickers.put(newStickers, emoji);
                                emojiArrays.add(newStickers);
                                added = true;
                            }
                        }
                    }
                    if (added) {
                        notifyDataSetChanged();
                    }
                }, false);
            }
            ArrayList<TLRPC.TL_messages_stickerSet> local = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_IMAGE);
            int index;
            for (int a = 0, size = local.size(); a < size; a++) {
                TLRPC.TL_messages_stickerSet set = local.get(a);
                if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                    if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                        clear();
                        localPacks.add(set);
                        localPacksByName.put(set, index);
                    }
                } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                    if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                        clear();
                        localPacks.add(set);
                        localPacksByShortName.put(set, true);
                    }
                }
            }
            local = MediaDataController.getInstance(currentAccount).getStickerSets(MediaDataController.TYPE_FEATURED);
            for (int a = 0, size = local.size(); a < size; a++) {
                TLRPC.TL_messages_stickerSet set = local.get(a);
                if ((index = AndroidUtilities.indexOfIgnoreCase(set.set.title, searchQuery)) >= 0) {
                    if (index == 0 || set.set.title.charAt(index - 1) == ' ') {
                        clear();
                        localPacks.add(set);
                        localPacksByName.put(set, index);
                    }
                } else if (set.set.short_name != null && (index = AndroidUtilities.indexOfIgnoreCase(set.set.short_name, searchQuery)) >= 0) {
                    if (index == 0 || set.set.short_name.charAt(index - 1) == ' ') {
                        clear();
                        localPacks.add(set);
                        localPacksByShortName.put(set, true);
                    }
                }
            }
            if ((!localPacks.isEmpty() || !emojiStickers.isEmpty())) {
                delegate.setAdapterVisible(true);
            }
            final TLRPC.TL_messages_searchStickerSets req = new TLRPC.TL_messages_searchStickerSets();
            req.q = searchQuery;
            reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_messages_foundStickerSets) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (req.q.equals(searchQuery)) {
                            clear();
                            delegate.onSearchStop();
                            reqId = 0;
                            delegate.setAdapterVisible(true);
                            TLRPC.TL_messages_foundStickerSets res = (TLRPC.TL_messages_foundStickerSets) response;
                            serverPacks.addAll(res.sets);
                            notifyDataSetChanged();
                        }
                    });
                }
            });
            if (Emoji.isValidEmoji(searchQuery)) {
                final TLRPC.TL_messages_getStickers req2 = new TLRPC.TL_messages_getStickers();
                req2.emoticon = searchQuery;
                req2.hash = 0;
                reqId2 = ConnectionsManager.getInstance(currentAccount).sendRequest(req2, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                    if (req2.emoticon.equals(searchQuery)) {
                        reqId2 = 0;
                        if (!(response instanceof TLRPC.TL_messages_stickers)) {
                            return;
                        }
                        TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
                        int oldCount = emojiStickersArray.size();
                        for (int a = 0, size = res.stickers.size(); a < size; a++) {
                            TLRPC.Document document = res.stickers.get(a);
                            if (emojiStickersMap.indexOfKey(document.id) >= 0) {
                                continue;
                            }
                            emojiStickersArray.add(document);
                        }
                        int newCount = emojiStickersArray.size();
                        if (oldCount != newCount) {
                            emojiStickers.put(emojiStickersArray, searchQuery);
                            if (oldCount == 0) {
                                emojiArrays.add(emojiStickersArray);
                            }
                            notifyDataSetChanged();
                        }
                    }
                }));
            }
            notifyDataSetChanged();
        }
    };
    private final Theme.ResourcesProvider resourcesProvider;

    public StickersSearchAdapter(Context context, Delegate delegate, TLRPC.StickerSetCovered[] primaryInstallingStickerSets, LongSparseArray<TLRPC.StickerSetCovered> installingStickerSets, LongSparseArray<TLRPC.StickerSetCovered> removingStickerSets, Theme.ResourcesProvider resourcesProvider) {
        this.context = context;
        this.delegate = delegate;
        this.primaryInstallingStickerSets = primaryInstallingStickerSets;
        this.installingStickerSets = installingStickerSets;
        this.removingStickerSets = removingStickerSets;
        this.resourcesProvider = resourcesProvider;
    }

    @Override
    public boolean isEnabled(RecyclerView.ViewHolder holder) {
        return false;
    }

    @Override
    public int getItemCount() {
        return Math.max(1, totalItems + 1);
    }

    public Object getItem(int i) {
        return cache.get(i);
    }

    public void search(String text) {
        if (reqId != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId, true);
            reqId = 0;
        }
        if (reqId2 != 0) {
            ConnectionsManager.getInstance(currentAccount).cancelRequest(reqId2, true);
            reqId2 = 0;
        }
        if (TextUtils.isEmpty(text)) {
            searchQuery = null;
            localPacks.clear();
            emojiStickers.clear();
            serverPacks.clear();
            delegate.setAdapterVisible(false);
            notifyDataSetChanged();
        } else {
            searchQuery = text.toLowerCase();
        }
        AndroidUtilities.cancelRunOnUIThread(searchRunnable);
        AndroidUtilities.runOnUIThread(searchRunnable, 300);
    }

    @Override
    public int getItemViewType(int position) {
        if (position == 0 && totalItems == 0) {
            return 5;
        } else if (position == getItemCount() - 1) {
            return 4;
        }
        Object object = cache.get(position);
        if (object != null) {
            if (object instanceof TLRPC.Document) {
                return 0;
            } else if (object instanceof TLRPC.StickerSetCovered) {
                return 3;
            } else {
                return 2;
            }
        }
        return 1;
    }

    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = null;
        switch (viewType) {
            case 0:
                StickerEmojiCell stickerEmojiCell = new StickerEmojiCell(context, false) {
                    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(82), MeasureSpec.EXACTLY));
                    }
                };
                view = stickerEmojiCell;
                stickerEmojiCell.getImageView().setLayerNum(3);
                break;
            case 1:
                view = new EmptyCell(context);
                break;
            case 2:
                view = new StickerSetNameCell(context, false, true, resourcesProvider);
                break;
            case 3:
                view = new FeaturedStickerSetInfoCell(context, 17, true, true, resourcesProvider);
                ((FeaturedStickerSetInfoCell) view).setAddOnClickListener(v -> {
                    final FeaturedStickerSetInfoCell cell = (FeaturedStickerSetInfoCell) v.getParent();
                    TLRPC.StickerSetCovered pack = cell.getStickerSet();
                    if (pack == null || installingStickerSets.indexOfKey(pack.set.id) >= 0 || removingStickerSets.indexOfKey(pack.set.id) >= 0) {
                        return;
                    }
                    if (cell.isInstalled()) {
                        removingStickerSets.put(pack.set.id, pack);
                        delegate.onStickerSetRemove(cell.getStickerSet());
                    } else {
                        installStickerSet(pack, cell);
                    }
                });
                break;
            case 4:
                view = new View(context);
                break;
            case 5:
                LinearLayout layout = new LinearLayout(context);
                layout.setOrientation(LinearLayout.VERTICAL);
                layout.setGravity(Gravity.CENTER);

                emptyImageView = new ImageView(context);
                emptyImageView.setScaleType(ImageView.ScaleType.CENTER);
                emptyImageView.setImageResource(R.drawable.stickers_empty);
                emptyImageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelEmptyText), PorterDuff.Mode.MULTIPLY));
                layout.addView(emptyImageView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

                layout.addView(new Space(context), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 15));

                emptyTextView = new TextView(context);
                emptyTextView.setText(LocaleController.getString("NoStickersFound", R.string.NoStickersFound));
                emptyTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
                emptyTextView.setTextColor(getThemedColor(Theme.key_chat_emojiPanelEmptyText));
                layout.addView(emptyTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));

                view = layout;
                view.setMinimumHeight(AndroidUtilities.dp(112));
                view.setLayoutParams(LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
                break;
        }

        return new RecyclerListView.Holder(view);
    }

    @Override
    public void onBindViewHolder(RecyclerView.ViewHolder holder, int position) {
        switch (holder.getItemViewType()) {
            case 0: {
                TLRPC.Document sticker = (TLRPC.Document) cache.get(position);
                StickerEmojiCell cell = (StickerEmojiCell) holder.itemView;
                cell.setSticker(sticker, null, cacheParent.get(position), positionToEmoji.get(position), false);
                //cell.setRecent(recentStickers.contains(sticker) || favouriteStickers.contains(sticker));
                break;
            }
            case 1: {
                EmptyCell cell = (EmptyCell) holder.itemView;
                cell.setHeight(0);
                break;
            }
            case 2: {
                StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                Object object = cache.get(position);
                if (object instanceof TLRPC.TL_messages_stickerSet) {
                    TLRPC.TL_messages_stickerSet set = (TLRPC.TL_messages_stickerSet) object;
                    if (!TextUtils.isEmpty(searchQuery) && localPacksByShortName.containsKey(set)) {
                        if (set.set != null) {
                            cell.setText(set.set.title, 0);
                        }
                        cell.setUrl(set.set.short_name, searchQuery.length());
                    } else {
                        Integer start = localPacksByName.get(set);
                        if (set.set != null && start != null) {
                            cell.setText(set.set.title, 0, start, !TextUtils.isEmpty(searchQuery) ? searchQuery.length() : 0);
                        }
                        cell.setUrl(null, 0);
                    }
                }
                break;
            }
            case 3: {
                bindFeaturedStickerSetInfoCell((FeaturedStickerSetInfoCell) holder.itemView, position, false);
                break;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position, @NonNull List payloads) {
        if (payloads.contains(PAYLOAD_ANIMATED)) {
            if (holder.getItemViewType() == 3) {
                bindFeaturedStickerSetInfoCell((FeaturedStickerSetInfoCell) holder.itemView, position, true);
                return;
            }
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    public void installStickerSet(TLRPC.InputStickerSet inputSet) {
        for (int i = 0; i < serverPacks.size(); i++) {
            final TLRPC.StickerSetCovered setCovered = serverPacks.get(i);
            if (setCovered.set.id == inputSet.id) {
                installStickerSet(setCovered, null);
                break;
            }
        }
    }

    public void installStickerSet(TLRPC.StickerSetCovered pack, FeaturedStickerSetInfoCell cell) {
        for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
            if (primaryInstallingStickerSets[i] != null) {
                final TLRPC.TL_messages_stickerSet s = MediaDataController.getInstance(currentAccount).getStickerSetById(primaryInstallingStickerSets[i].set.id);
                if (s != null && !s.set.archived) {
                    primaryInstallingStickerSets[i] = null;
                    break;
                }
                if (primaryInstallingStickerSets[i].set.id == pack.set.id) {
                    return;
                }
            }
        }

        boolean primary = false;
        for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
            if (primaryInstallingStickerSets[i] == null) {
                primaryInstallingStickerSets[i] = pack;
                primary = true;
                break;
            }
        }
        if (!primary && cell != null) {
            cell.setAddDrawProgress(true, true);
        }
        installingStickerSets.put(pack.set.id, pack);
        if (cell != null) {
            delegate.onStickerSetAdd(cell.getStickerSet(), primary);
        } else {
            for (int i = 0, size = positionsToSets.size(); i < size; i++) {
                final TLRPC.StickerSetCovered item = positionsToSets.get(i);
                if (item != null && item.set.id == pack.set.id) {
                    notifyItemChanged(i, PAYLOAD_ANIMATED);
                    break;
                }
            }
        }
    }

    private void bindFeaturedStickerSetInfoCell(FeaturedStickerSetInfoCell cell, int position, boolean animated) {
        final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
        ArrayList<Long> unreadStickers = mediaDataController.getUnreadStickerSets();
        TLRPC.StickerSetCovered stickerSetCovered = (TLRPC.StickerSetCovered) cache.get(position);
        boolean unread = unreadStickers != null && unreadStickers.contains(stickerSetCovered.set.id);
        boolean forceInstalled = false;
        for (int i = 0; i < primaryInstallingStickerSets.length; i++) {
            if (primaryInstallingStickerSets[i] != null) {
                final TLRPC.TL_messages_stickerSet s = MediaDataController.getInstance(currentAccount).getStickerSetById(primaryInstallingStickerSets[i].set.id);
                if (s != null && !s.set.archived) {
                    primaryInstallingStickerSets[i] = null;
                    continue;
                }
                if (primaryInstallingStickerSets[i].set.id == stickerSetCovered.set.id) {
                    forceInstalled = true;
                    break;
                }
            }
        }

        int idx = TextUtils.isEmpty(searchQuery) ? -1 : AndroidUtilities.indexOfIgnoreCase(stickerSetCovered.set.title, searchQuery);
        if (idx >= 0) {
            cell.setStickerSet(stickerSetCovered, unread, animated, idx, searchQuery.length(), forceInstalled);
        } else {
            cell.setStickerSet(stickerSetCovered, unread, animated, 0, 0, forceInstalled);
            if (!TextUtils.isEmpty(searchQuery) && AndroidUtilities.indexOfIgnoreCase(stickerSetCovered.set.short_name, searchQuery) == 0) {
                cell.setUrl(stickerSetCovered.set.short_name, searchQuery.length());
            }
        }

        if (unread) {
            mediaDataController.markFeaturedStickersByIdAsRead(false, stickerSetCovered.set.id);
        }

        boolean installing = installingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
        boolean removing = removingStickerSets.indexOfKey(stickerSetCovered.set.id) >= 0;
        if (installing || removing) {
            if (installing && cell.isInstalled()) {
                installingStickerSets.remove(stickerSetCovered.set.id);
                installing = false;
            } else if (removing && !cell.isInstalled()) {
                removingStickerSets.remove(stickerSetCovered.set.id);
            }
        }
        cell.setAddDrawProgress(!forceInstalled && installing, animated);
        mediaDataController.preloadStickerSetThumb(stickerSetCovered);
        cell.setNeedDivider(position > 0);
    }

    @Override
    public void notifyDataSetChanged() {
        rowStartPack.clear();
        positionToRow.clear();
        cache.clear();
        positionsToSets.clear();
        positionToEmoji.clear();
        totalItems = 0;
        int startRow = 0;
        for (int a = 0, serverCount = serverPacks.size(), localCount = localPacks.size(), emojiCount = (emojiArrays.isEmpty() ? 0 : 1); a < serverCount + localCount + emojiCount; a++) {
            ArrayList<TLRPC.Document> documents;
            Object pack = null;
            String key;
            int idx = a;
            if (idx < localCount) {
                TLRPC.TL_messages_stickerSet set = localPacks.get(idx);
                documents = set.documents;
                pack = set;
            } else {
                idx -= localCount;
                if (idx < emojiCount) {
                    int documentsCount = 0;
                    String lastEmoji = "";
                    for (int i = 0, N = emojiArrays.size(); i < N; i++) {
                        documents = emojiArrays.get(i);
                        String emoji = emojiStickers.get(documents);
                        if (emoji != null && !lastEmoji.equals(emoji)) {
                            lastEmoji = emoji;
                            positionToEmoji.put(totalItems + documentsCount, lastEmoji);
                        }
                        for (int b = 0, size = documents.size(); b < size; b++) {
                            int num = documentsCount + totalItems;
                            int row = startRow + documentsCount / delegate.getStickersPerRow();

                            TLRPC.Document document = documents.get(b);
                            cache.put(num, document);
                            Object parent = MediaDataController.getInstance(currentAccount).getStickerSetById(MediaDataController.getStickerSetId(document));
                            if (parent != null) {
                                cacheParent.put(num, parent);
                            }
                            positionToRow.put(num, row);
                            if (a >= localCount && pack instanceof TLRPC.StickerSetCovered) {
                                positionsToSets.put(num, (TLRPC.StickerSetCovered) pack);
                            }
                            documentsCount++;
                        }
                    }
                    int count = (int) Math.ceil(documentsCount / (float) delegate.getStickersPerRow());
                    for (int b = 0; b < count; b++) {
                        rowStartPack.put(startRow + b, documentsCount);
                    }
                    totalItems += count * delegate.getStickersPerRow();
                    startRow += count;
                    continue;
                } else {
                    idx -= emojiCount;
                    TLRPC.StickerSetCovered set = serverPacks.get(idx);
                    documents = set.covers;
                    pack = set;
                }
            }
            if (documents.isEmpty()) {
                continue;
            }
            int count = (int) Math.ceil(documents.size() / (float) delegate.getStickersPerRow());
            cache.put(totalItems, pack);
            if (a >= localCount && pack instanceof TLRPC.StickerSetCovered) {
                positionsToSets.put(totalItems, (TLRPC.StickerSetCovered) pack);
            }
            positionToRow.put(totalItems, startRow);
            for (int b = 0, size = documents.size(); b < size; b++) {
                int num = 1 + b + totalItems;
                int row = startRow + 1 + b / delegate.getStickersPerRow();
                TLRPC.Document document = documents.get(b);
                cache.put(num, document);
                if (pack != null) {
                    cacheParent.put(num, pack);
                }
                positionToRow.put(num, row);
                if (a >= localCount && pack instanceof TLRPC.StickerSetCovered) {
                    positionsToSets.put(num, (TLRPC.StickerSetCovered) pack);
                }
            }
            for (int b = 0, N = count + 1; b < N; b++) {
                rowStartPack.put(startRow + b, pack);
            }
            totalItems += 1 + count * delegate.getStickersPerRow();
            startRow += count + 1;
        }
        super.notifyDataSetChanged();
    }

    public int getSpanSize(int position) {
        if (position != totalItems) {
            Object object = cache.get(position);
            if (object == null || cache.get(position) instanceof TLRPC.Document) {
                return 1;
            }
        }
        return delegate.getStickersPerRow();
    }

    public TLRPC.StickerSetCovered getSetForPosition(int position) {
        return positionsToSets.get(position);
    }

    public void updateColors(RecyclerListView listView) {
        for (int i = 0, size = listView.getChildCount(); i < size; i++) {
            final View child = listView.getChildAt(i);
            if (child instanceof FeaturedStickerSetInfoCell) {
                ((FeaturedStickerSetInfoCell) child).updateColors();
            } else if (child instanceof StickerSetNameCell) {
                ((StickerSetNameCell) child).updateColors();
            }
        }
    }

    public void getThemeDescriptions(List<ThemeDescription> descriptions, RecyclerListView listView, ThemeDescription.ThemeDescriptionDelegate delegate) {
        FeaturedStickerSetInfoCell.createThemeDescriptions(descriptions, listView, delegate);
        StickerSetNameCell.createThemeDescriptions(descriptions, listView, delegate);
        descriptions.add(new ThemeDescription(emptyImageView, ThemeDescription.FLAG_IMAGECOLOR, null, null, null, null, Theme.key_chat_emojiPanelEmptyText));
        descriptions.add(new ThemeDescription(emptyTextView, ThemeDescription.FLAG_TEXTCOLOR, null, null, null, null, Theme.key_chat_emojiPanelEmptyText));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}