package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.translitSafe;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.media.Image;
import android.os.Build;
import android.os.SystemClock;
import android.text.Editable;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Log;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.OvershootInterpolator;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.OverScroller;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.core.math.MathUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.checkerframework.checker.units.qual.A;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DocumentObject;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.StickerSetNameCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedEmojiSpan;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DrawingInBackgroundThreadDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmojiTabsStrip;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.ScaleStateListAnimator;
import org.telegram.ui.Components.SearchStateDrawable;
import org.telegram.ui.Components.StickerCategoriesListView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.SelectAnimatedEmojiDialog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

public class EmojiBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private static final int PAGE_TYPE_EMOJI = 0;
    private static final int PAGE_TYPE_STICKERS = 1;

    private String query = null;
    private int categoryIndex = -1;

    private class Page extends FrameLayout {
        public EmojiListView listView;
        public Adapter adapter;
        public GridLayoutManager layoutManager;
        public EmojiTabsStrip tabsStrip;
        public SearchField searchField;

        public int spanCount = 8;

        public int currentType;

        public Page(Context context) {
            super(context);

            listView = new EmojiListView(context);
            listView.setAdapter(adapter = new Adapter());
            listView.setLayoutManager(layoutManager = new GridLayoutManager(context, spanCount));
            listView.setClipToPadding(true);
            listView.setVerticalScrollBarEnabled(false);
            layoutManager.setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    if (adapter.getItemViewType(position) != Adapter.VIEW_TYPE_EMOJI) {
                        return spanCount;
                    }
                    return 1;
                }
            });
            listView.setOnItemClickListener((view, position) -> {
                if (position < 0) {
                    return;
                }
                TLRPC.Document document = position >= adapter.documents.size() ? null : adapter.documents.get(position);
                long documentId = position >= adapter.documentIds.size() ? 0L : adapter.documentIds.get(position);
                if (document == null && view instanceof EmojiListView.EmojiImageView && ((EmojiListView.EmojiImageView) view).drawable != null) {
                    document = ((EmojiListView.EmojiImageView) view).drawable.getDocument();
                }
                if (document == null && documentId != 0L) {
                    document = AnimatedEmojiDrawable.findDocument(currentAccount, documentId);
                }
                if (document == null) {
                    return;
                }
                if (onDocumentSelected != null) {
                    onDocumentSelected.run(document);
                }
                dismiss();
            });
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    containerView.invalidate();
                    int pos;
                    if (lockTop < 0) {
                        pos = layoutManager.findFirstCompletelyVisibleItemPosition();
                    } else {
                        pos = -1;
                        for (int i = 0; i < listView.getChildCount(); ++i) {
                            View child = listView.getChildAt(i);
                            if (child.getY() + child.getHeight() > lockTop + listView.getPaddingTop()) {
                                pos = listView.getChildAdapterPosition(child);
                                break;
                            }
                        }
                        if (pos == -1) {
                            return;
                        }
                    }
                    int sec = -1;
                    for (int i = adapter.positionToSection.size() - 1; i >= 0; --i) {
                        int position = adapter.positionToSection.keyAt(i);
                        int section = adapter.positionToSection.valueAt(i);
                        if (pos >= position) {
                            sec = section;
                            break;
                        }
                    }
                    if (sec >= 0) {
                        tabsStrip.select(sec, true);
                    }
                    if (keyboardVisible && listView.scrollingByUser && searchField != null && searchField.editText != null) {
                        closeKeyboard();
                    }
                }

                @Override
                public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                    if (newState == RecyclerView.SCROLL_STATE_IDLE && lockTop >= 0 && atTop()) {
                        lockTop = -1;
                    }
                }
            });
            DefaultItemAnimator itemAnimator = new DefaultItemAnimator();
            itemAnimator.setAddDelay(0);
            itemAnimator.setAddDuration(220);
            itemAnimator.setMoveDuration(220);
            itemAnimator.setChangeDuration(160);
            itemAnimator.setMoveInterpolator(CubicBezierInterpolator.EASE_OUT);
            listView.setItemAnimator(itemAnimator);
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            searchField = new SearchField(context, resourcesProvider);
            searchField.setOnSearchQuery((query, category) -> {
                EmojiBottomSheet.this.query = query;
                EmojiBottomSheet.this.categoryIndex = category;
                adapter.updateItems(query);
            });
            addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));

            tabsStrip = new EmojiTabsStrip(context, resourcesProvider, false, false, true, 0, null) {
                @Override
                protected boolean onTabClick(int index) {
                    if (scrollingAnimation) {
                        return false;
                    }
                    if (searchField != null && searchField.categoriesListView != null) {
                        if (searchField.categoriesListView.getSelectedCategory() != null) {
                            listView.scrollToPosition(0, 0);
                            searchField.categoriesListView.selectCategory(null);
                        }
                        searchField.categoriesListView.scrollToStart();
                        searchField.clear();
                    }
                    if (adapter != null) {
                        adapter.updateItems(null);
                    }
                    int pos = -1;
                    for (int i = 0; i < adapter.positionToSection.size(); ++i) {
                        int position = adapter.positionToSection.keyAt(i);
                        int section = adapter.positionToSection.valueAt(i);
                        if (section == index) {
                            pos = position;
                            break;
                        }
                    }
                    if (pos >= 0) {
                        listView.scrollToPosition(pos, (int) lockTop() - dp(16 + 36 + 50));
                    }
                    return true;
                }
            };
            addView(tabsStrip, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36));
        }

        private float lockTop = -1;
        public float top() {
            if (lockTop >= 0) {
                return lockTop;
            }
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof Integer && (int) tag == 34) {
                    return Math.max(0, child.getBottom() - dp(16 + 36 + 50));
                }
            }
            return 0;
        }

        public float lockTop() {
            if (lockTop >= 0) {
                return lockTop + listView.getPaddingTop();
            }
            lockTop = top();
            return lockTop + listView.getPaddingTop();
        }

        public void updateTops() {
            float top = Math.max(0, top());
            tabsStrip.setTranslationY(dp(16) + top);
            searchField.setTranslationY(dp(16 + 36) + top);
            listView.setBounds(top + listView.getPaddingTop(), listView.getHeight() - listView.getPaddingBottom());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, 0);
            tabsStrip.setTranslationY(dp(16));
            searchField.setTranslationY(dp(16 + 36));
            listView.setPadding(dp(5), dp(16 + 36 + 50), dp(5), AndroidUtilities.navigationBarHeight + dp(40));
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private boolean resetOnce = false;

        public void bind(int type) {
            currentType = type;
            layoutManager.setSpanCount(spanCount = type == PAGE_TYPE_EMOJI ? 8 : 5);
            if (!resetOnce) {
                adapter.updateItems(null);
            }
            if (categoryIndex >= 0) {
                searchField.ignoreTextChange = true;
                searchField.editText.setText("");
                searchField.ignoreTextChange = false;
                searchField.categoriesListView.selectCategory(categoryIndex);
                searchField.categoriesListView.scrollToSelected();
                StickerCategoriesListView.EmojiCategory category = searchField.categoriesListView.getSelectedCategory();
                if (category != null) {
                    adapter.query = searchField.categoriesListView.getSelectedCategory().emojis;
                    AndroidUtilities.cancelRunOnUIThread(adapter.searchRunnable);
                    AndroidUtilities.runOnUIThread(adapter.searchRunnable);
                }
            } else if (!TextUtils.isEmpty(query)) {
                searchField.editText.setText(query);
                searchField.categoriesListView.selectCategory(null);
                searchField.categoriesListView.scrollToStart();
                AndroidUtilities.cancelRunOnUIThread(adapter.searchRunnable);
                AndroidUtilities.runOnUIThread(adapter.searchRunnable);
            } else {
                searchField.clear();
            }

            MediaDataController.getInstance(currentAccount).checkStickers(type == PAGE_TYPE_EMOJI ? MediaDataController.TYPE_EMOJIPACKS : MediaDataController.TYPE_IMAGE);
        }

        public boolean atTop() {
            return !listView.canScrollVertically(-1);
        }

        private class Adapter extends RecyclerView.Adapter {

            private int lastAllSetsCount;
            private final HashMap<String, ArrayList<Long>> allEmojis = new HashMap<>();
            private final HashMap<Long, ArrayList<TLRPC.TL_stickerPack>> packsBySet = new HashMap<>();

            private final ArrayList<TLRPC.TL_messages_stickerSet> allStickerSets = new ArrayList<>();
            private final ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
            private final ArrayList<EmojiView.EmojiPack> packs = new ArrayList<>();
            private final ArrayList<TLRPC.Document> documents = new ArrayList<>();
            private final ArrayList<Long> documentIds = new ArrayList<>();
            private boolean includeNotFound;
            private int itemsCount = 0;
            private final SparseIntArray positionToSection = new SparseIntArray();

            public void update() {
                if (this.query == null) {
                    updateItems(null);
                }
            }

            private TLRPC.TL_messages_stickerSet faveSet;
            private TLRPC.TL_messages_stickerSet recentSet;

            private void updateItems(String query) {
                this.query = query;
                if (query != null) {
                    searchField.showProgress(true);
                    tabsStrip.showSelected(false);
                    AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                    AndroidUtilities.runOnUIThread(searchRunnable, 100);
                    return;
                }

                tabsStrip.showSelected(true);
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);

                final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);

                itemsCount = 0;
                documents.clear();
                documentIds.clear();
                positionToSection.clear();
                stickerSets.clear();
                allStickerSets.clear();
                itemsCount++; // pan
                documents.add(null);
                packs.clear();
                if (currentType == PAGE_TYPE_STICKERS) {
                    ArrayList<TLRPC.Document> favorites = mediaDataController.getRecentStickers(MediaDataController.TYPE_FAVE);
                    if (favorites != null && !favorites.isEmpty()) {
                        if (faveSet == null) {
                            faveSet = new TLRPC.TL_messages_stickerSet();
                        }
                        faveSet.documents = favorites;
                        faveSet.set = new TLRPC.TL_stickerSet();
                        faveSet.set.title = LocaleController.getString("FavoriteStickers", R.string.FavoriteStickers);
                        stickerSets.add(faveSet);
                    }

                    ArrayList<TLRPC.Document> recent = mediaDataController.getRecentStickers(MediaDataController.TYPE_IMAGE);
                    if (recent != null && !recent.isEmpty()) {
                        if (recentSet == null) {
                            recentSet = new TLRPC.TL_messages_stickerSet();
                        }
                        recentSet.documents = recent;
                        recentSet.set = new TLRPC.TL_stickerSet();
                        recentSet.set.title = LocaleController.getString("RecentStickers", R.string.RecentStickers);
                        stickerSets.add(recentSet);
                    }
                }
                stickerSets.addAll(mediaDataController.getStickerSets(
                        currentType == PAGE_TYPE_EMOJI ?
                                MediaDataController.TYPE_EMOJIPACKS :
                                MediaDataController.TYPE_IMAGE
                ));
                int i = 0;
                for (; i < stickerSets.size(); ++i) {
                    TLRPC.TL_messages_stickerSet set = stickerSets.get(i);

                    // header
                    positionToSection.put(itemsCount, i);
                    documents.add(null);
                    itemsCount++;

                    // emoji/stickers
                    documents.addAll(set.documents);
                    itemsCount += set.documents.size();

                    EmojiView.EmojiPack pack = new EmojiView.EmojiPack();
                    pack.documents = set.documents;
                    pack.set = set.set;
                    pack.installed = true;
                    pack.featured = false;
                    pack.expanded = true;
                    pack.free = true;
                    if (set == faveSet) {
                        pack.resId = R.drawable.emoji_tabs_faves;
                    } else if (set == recentSet) {
                        pack.resId = R.drawable.msg_emoji_recent;
                    }
                    packs.add(pack);

                    allStickerSets.add(set);
                }
                if (currentType == PAGE_TYPE_EMOJI) {
                    ArrayList<TLRPC.StickerSetCovered> featuredSets = mediaDataController.getFeaturedEmojiSets();
                    if (featuredSets != null) {
                        for (int j = 0; j < featuredSets.size(); ++j) {
                            TLRPC.StickerSetCovered setCovered = featuredSets.get(j);
                            TLRPC.TL_messages_stickerSet set;
                            if (setCovered instanceof TLRPC.TL_stickerSetNoCovered) {
                                set = MediaDataController.getInstance(currentAccount).getStickerSet(MediaDataController.getInputStickerSet(setCovered.set), false);
                                if (set == null) {
                                    continue;
                                }
                            } else if (setCovered instanceof TLRPC.TL_stickerSetFullCovered) {
                                set = new TLRPC.TL_messages_stickerSet();
                                set.set = setCovered.set;
                                set.documents = ((TLRPC.TL_stickerSetFullCovered) setCovered).documents;
                                set.packs = packsBySet.get(set.set.id);
                                if (set.packs == null) {
                                    HashMap<String, ArrayList<Long>> packs = new HashMap<>();
                                    for (int a = 0; a < set.documents.size(); ++a) {
                                        TLRPC.Document document = set.documents.get(a);
                                        if (document == null) {
                                            continue;
                                        }
                                        String emoticon = MessageObject.findAnimatedEmojiEmoticon(document, null);
                                        ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(emoticon);
                                        if (emojis != null) {
                                            for (int e = 0; e < emojis.size(); ++e) {
                                                String emoji = emojis.get(e).code.toString();
                                                ArrayList<Long> list = packs.get(emoji);
                                                if (list == null) {
                                                    packs.put(emoji, list = new ArrayList<>());
                                                }
                                                list.add(document.id);
                                            }
                                        }
                                    }
                                    set.packs = new ArrayList<>();
                                    for (Map.Entry<String, ArrayList<Long>> e : packs.entrySet()) {
                                        TLRPC.TL_stickerPack pack = new TLRPC.TL_stickerPack();
                                        pack.emoticon = e.getKey();
                                        pack.documents = e.getValue();
                                        set.packs.add(pack);
                                    }
                                    packsBySet.put(set.set.id, set.packs);
                                }
                            } else {
                                continue;
                            }

                            boolean found = false;
                            if (set == null || set.set == null) {
                                continue;
                            }
                            for (int a = 0; a < packs.size(); ++a) {
                                TLRPC.StickerSet set2 = packs.get(a).set;
                                if (set2 != null && set2.id == set.set.id) {
                                    found = true;
                                    break;
                                }
                            }
                            if (found) {
                                continue;
                            }

                            stickerSets.add(set);
                            allStickerSets.add(set);

                            // header
                            positionToSection.put(itemsCount, i);
                            i++;
                            documents.add(null);
                            itemsCount++;

                            // emoji/stickers
                            documents.addAll(set.documents);
                            itemsCount += set.documents.size();

                            EmojiView.EmojiPack pack = new EmojiView.EmojiPack();
                            pack.documents = set.documents;
                            pack.set = set.set;
                            pack.installed = false;
                            pack.featured = true;
                            pack.expanded = true;
                            pack.free = true;
                            packs.add(pack);
                        }
                    }
                    boolean containsStaticEmoji = false;
                    for (int a = 0; a < allStickerSets.size(); ++a) {
                        try {
                            containsStaticEmoji = allStickerSets.get(a).set.title.toLowerCase().contains("staticemoji");
                        } catch (Exception ignore) {}
                        if (containsStaticEmoji) {
                            break;
                        }
                    }
                    if (!containsStaticEmoji) {
                        TLRPC.TL_inputStickerSetShortName inputStickerSet = new TLRPC.TL_inputStickerSetShortName();
                        inputStickerSet.short_name = "StaticEmoji";
                        TLRPC.TL_messages_stickerSet set = mediaDataController.getStickerSet(inputStickerSet, false);
                        if (set != null) {
                            allStickerSets.add(set);
                        }
                    }
                }
                resetOnce = true;

                if (lastAllSetsCount != allStickerSets.size()) {
                    allEmojis.clear();
                    for (int a = 0; a < allStickerSets.size(); ++a) {
                        TLRPC.TL_messages_stickerSet set = allStickerSets.get(a);
                        if (set == null) {
                            continue;
                        }
                        for (int b = 0; b < set.packs.size(); ++b) {
                            String emoji = set.packs.get(b).emoticon;
                            ArrayList<Long> arr = allEmojis.get(emoji);
                            if (arr == null) {
                                allEmojis.put(emoji, arr = new ArrayList<>());
                            }
                            arr.addAll(set.packs.get(b).documents);
                        }
                    }
                    lastAllSetsCount = allStickerSets.size();
                }

                includeNotFound = false;
                tabsStrip.updateEmojiPacks(packs);
                activeQuery = null;
                notifyDataSetChanged();
            }

            private String query;
            private String activeQuery;
            private String[] lastLang;
            private int searchId;

            private HashSet<Long> searchDocumentIds = new HashSet<>();

            private final Runnable searchRunnable = () -> {
                final String thisQuery = query;
                final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                String[] lang = AndroidUtilities.getCurrentKeyboardLanguage();
                if (lastLang == null || !Arrays.equals(lang, lastLang)) {
                    MediaDataController.getInstance(currentAccount).fetchNewEmojiKeywords(lang);
                }
                mediaDataController.getEmojiSuggestions(lastLang = lang, query, false, (result, alias) -> {
                    if (!TextUtils.equals(thisQuery, query)) {
                        return;
                    }

                    ArrayList<Emoji.EmojiSpanRange> emojis = Emoji.parseEmojis(query);
                    for (int i = 0; i < emojis.size(); ++i) {
                        try {
                            MediaDataController.KeywordResult k = new MediaDataController.KeywordResult();
                            k.emoji = emojis.get(i).code.toString();
                            result.add(k);
                        } catch (Exception ignore) {}
                    }

                    itemsCount = 0;
                    documents.clear();
                    documentIds.clear();
                    positionToSection.clear();
                    stickerSets.clear();
                    itemsCount++; // pan
                    documents.add(null);
                    documentIds.add(0L);
                    if (currentType == PAGE_TYPE_EMOJI) {
                        searchDocumentIds.clear();
                        for (int i = 0; i < result.size(); ++i) {
                            MediaDataController.KeywordResult r = result.get(i);
                            if (r.emoji != null && !r.emoji.startsWith("animated_")) {
                                ArrayList<Long> ids = allEmojis.get(r.emoji);
                                if (ids != null) {
                                    searchDocumentIds.addAll(ids);
                                }
                            }
                        }
                        documentIds.addAll(searchDocumentIds);
                        for (int i = 0; i < searchDocumentIds.size(); ++i) {
                            documents.add(null);
                        }
                        itemsCount += searchDocumentIds.size();
                    } else {
                        final HashMap<String, ArrayList<TLRPC.Document>> allStickers = mediaDataController.getAllStickers();
                        for (int i = 0; i < result.size(); ++i) {
                            MediaDataController.KeywordResult r = result.get(i);
                            ArrayList<TLRPC.Document> stickers = allStickers.get(r.emoji);
                            if (stickers == null || stickers.isEmpty()) {
                                continue;
                            }
                            for (int j = 0; j < stickers.size(); ++j) {
                                TLRPC.Document d = stickers.get(j);
                                if (d != null && !documents.contains(d)) {
                                    documents.add(d);
                                    itemsCount++;
                                }
                            }
                        }
                    }
                    final String q = translitSafe((query + "").toLowerCase());
                    for (int i = 0; i < allStickerSets.size(); ++i) {
                        TLRPC.TL_messages_stickerSet set = allStickerSets.get(i);
                        if (set == null || set.set == null) {
                            continue;
                        }
                        final String title = translitSafe((set.set.title + "").toLowerCase());
                        if (title.startsWith(q) || title.contains(" " + q)) {
                            final int index = stickerSets.size();
                            stickerSets.add(set);

                            // header
                            positionToSection.put(itemsCount, index);
                            documents.add(null);
                            itemsCount++;

                            // emoji/stickers
                            documents.addAll(set.documents);
                            itemsCount += set.documents.size();
                        }
                    }

                    if (includeNotFound = (documentIds.size() <= 1 && documents.size() <= 1)) {
                        itemsCount++;
                    }
                    if (!includeNotFound) {
                        searchId++;
                    }
                    activeQuery = query;
                    notifyDataSetChanged();

                    listView.scrollToPosition(0, 0);
                    searchField.showProgress(false);
                    tabsStrip.showSelected(false);
                }, null, false, false, false, true, 50);
            };

            private static final int VIEW_TYPE_PAD = 0;
            private static final int VIEW_TYPE_HEADER = 1;
            private static final int VIEW_TYPE_EMOJI = 2;
            private static final int VIEW_TYPE_NOT_FOUND = 3;

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == VIEW_TYPE_PAD) {
                    view = new View(getContext());
                } else if (viewType == VIEW_TYPE_HEADER) {
                    view = new StickerSetNameCell(getContext(), true, resourcesProvider);
                } else if (viewType == VIEW_TYPE_NOT_FOUND) {
                    view = new NoEmojiView(getContext(), currentType == PAGE_TYPE_EMOJI);
                } else {
                    view = new EmojiListView.EmojiImageView(getContext(), listView);
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                final int viewType = holder.getItemViewType();
                if (viewType == VIEW_TYPE_PAD) {
                    holder.itemView.setTag(34);
                    holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) maxPadding));
                } else if (viewType == VIEW_TYPE_HEADER) {
                    final int section = positionToSection.get(position);
                    if (section < 0 || section >= stickerSets.size()) {
                        return;
                    }
                    final TLRPC.TL_messages_stickerSet set = stickerSets.get(section);
                    String title = set == null || set.set == null ? "" : set.set.title;
                    StickerSetNameCell cell = (StickerSetNameCell) holder.itemView;
                    if (activeQuery == null) {
                        cell.setText(title, 0);
                    } else {
                        int index = title.toLowerCase().indexOf(activeQuery.toLowerCase());
                        if (index < 0) {
                            cell.setText(title, 0);
                        } else {
                            cell.setText(title, 0, index, activeQuery.length());
                        }
                    }
                } else if (viewType == VIEW_TYPE_EMOJI) {
                    final TLRPC.Document document = position >= documents.size() ? null : documents.get(position);
                    final long documentId = position >= documentIds.size() ? 0L : documentIds.get(position);
                    if (document == null && documentId == 0) {
                        return;
                    }
                    EmojiListView.EmojiImageView imageView = (EmojiListView.EmojiImageView) holder.itemView;
                    if (currentType == PAGE_TYPE_EMOJI) {
                        if (document != null) {
                            imageView.setSticker(null);
                            imageView.setEmoji(document);
                        } else {
                            imageView.setSticker(null);
                            imageView.setEmojiId(documentId);
                        }
                    } else {
                        imageView.setEmoji(null);
                        imageView.setSticker(document);
                    }
                } else if (viewType == VIEW_TYPE_NOT_FOUND) {
                    ((NoEmojiView) holder.itemView).update(searchId);
                }
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0) {
                    return VIEW_TYPE_PAD;
                } else if (includeNotFound && position == itemsCount - 1) {
                    return VIEW_TYPE_NOT_FOUND;
                } else if (positionToSection.get(position, -1) >= 0) {
                    return VIEW_TYPE_HEADER;
                } else {
                    return VIEW_TYPE_EMOJI;
                }
            }

            @Override
            public int getItemCount() {
                return itemsCount;
            }
        }
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.stickersDidLoad || id == NotificationCenter.groupStickersDidLoad) {
            View[] pages = viewPager.getViewPages();
            for (int i = 0; i < pages.length; ++i) {
                View view = pages[i];
                if (view instanceof Page) {
                    Page page = (Page) view;
                    if (id == NotificationCenter.groupStickersDidLoad ||
                        page.currentType == PAGE_TYPE_EMOJI && (int) args[0] == MediaDataController.TYPE_EMOJIPACKS ||
                        page.currentType == PAGE_TYPE_STICKERS && (int) args[0] == MediaDataController.TYPE_IMAGE
                    ) {
                        page.adapter.update();
                    }
                }
            }
        }
    }

    private final ViewPagerFixed viewPager;
    private final TabsView tabsView;
    private final ImageView galleryButton;
    private float maxPadding = -1;

//    private final GestureDetector gestureDetector;
    private boolean wasKeyboardVisible;

    public EmojiBottomSheet(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context, true, resourcesProvider);

        useSmoothKeyboard = true;
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        occupyNavigationBar = true;
        setUseLightStatusBar(false);

        containerView = new ContainerView(context);
        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onTabAnimationUpdate() {
                tabsView.setType(viewPager.getPositionAnimated());
                containerView.invalidate();
                invalidate();
            }
        };
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return 2;
            }
            @Override
            public View createView(int viewType) {
                return new Page(context);
            }
            @Override
            public void bindView(View view, int position, int viewType) {
                ((Page) view).bind(position);
            }
        });
        containerView.addView(viewPager, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        new KeyboardNotifier(containerView, height -> {
            if (wasKeyboardVisible != keyboardVisible) {
                wasKeyboardVisible = keyboardVisible;
                container.clearAnimation();
                final float ty = keyboardVisible ? Math.min(0, Math.max((AndroidUtilities.displaySize.y - keyboardHeight) * .3f - top, -keyboardHeight / 3f)) : 0;
                container.animate().translationY(ty).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
            }
        });

        tabsView = new TabsView(context);
        tabsView.setOnTypeSelected(type -> {
            if (!viewPager.isManualScrolling() && viewPager.getCurrentPosition() != type) {
                viewPager.scrollToPosition(type);
                tabsView.setType(type);
            }
        });
        containerView.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));

        galleryButton = new ImageView(context);
        galleryButton.setScaleType(ImageView.ScaleType.CENTER);
        galleryButton.setVisibility(View.GONE);
        galleryButton.setImageResource(R.drawable.msg_tabs_media);
        galleryButton.setColorFilter(new PorterDuffColorFilter(0x70ffffff, PorterDuff.Mode.SRC_IN));
        ScaleStateListAnimator.apply(galleryButton);
        containerView.addView(galleryButton, LayoutHelper.createFrame(40, 40, Gravity.BOTTOM | Gravity.LEFT, 8, 0, 0, 0));

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);

        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
        MediaDataController.getInstance(currentAccount).checkFeaturedEmoji();

        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
    }

    public void closeKeyboard() {
        keyboardVisible = false;
        container.animate().translationY(0).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
        View[] views = viewPager.getViewPages();
        for (int i = 0; i < views.length; ++i) {
            View view = views[i];
            if (view instanceof Page && ((Page) view).searchField != null) {
                AndroidUtilities.hideKeyboard(((Page) view).searchField.editText);
            }
        }
    }

    public void setOnGalleryClick(View.OnClickListener listener) {
        galleryButton.setOnClickListener(listener);
        galleryButton.setVisibility(listener != null ? View.VISIBLE : View.GONE);
    }

    @Override
    public void dismiss() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
        closeKeyboard();
        super.dismiss();
    }

    private Utilities.Callback2<Bitmap, Float> drawBlurBitmap;

    public void setBlurDelegate(Utilities.Callback2<Bitmap, Float> drawBlurBitmap) {
        this.drawBlurBitmap = drawBlurBitmap;
    }

    private float top;

    private class ContainerView extends FrameLayout {

        private static final float PADDING = .45f;

        private final Paint backgroundPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint backgroundBlurPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Bitmap blurBitmap;
        private BitmapShader blurBitmapShader;
        private Matrix blurBitmapMatrix;

        private final AnimatedFloat isActionBarT = new AnimatedFloat(this, 0, 250, CubicBezierInterpolator.EASE_OUT_QUINT);
        private final RectF handleRect = new RectF();

        public ContainerView(Context context) {
            super(context);
        }

        @Override
        protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
            super.onLayout(changed, left, top, right, bottom);
            setupBlurBitmap();
        }

        private void setupBlurBitmap() {
            if (blurBitmap != null || drawBlurBitmap == null || SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_LOW || !LiteMode.isPowerSaverApplied()) {
                return;
            }
            final int scale = 16;
            Bitmap bitmap = Bitmap.createBitmap(AndroidUtilities.displaySize.x / scale, AndroidUtilities.displaySize.y / scale, Bitmap.Config.ARGB_8888);
            drawBlurBitmap.run(bitmap, (float) scale);
            Utilities.stackBlurBitmap(bitmap, 8);
            blurBitmap = bitmap;
            backgroundBlurPaint.setShader(blurBitmapShader = new BitmapShader(blurBitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
            if (blurBitmapMatrix == null) {
                blurBitmapMatrix = new Matrix();
            }
            blurBitmapMatrix.postScale(8, 8);
            blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
            invalidate();
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            if (blurBitmap != null) {
                blurBitmap.recycle();
            }
            backgroundBlurPaint.setShader(null);
        }

        @Override
        public void setTranslationY(float translationY) {
            super.setTranslationY(translationY);
            invalidate();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int height = MeasureSpec.getSize(heightMeasureSpec);
            final float newMaxPadding = Math.min(height * PADDING, dp(350) / (1f - PADDING) * PADDING);
//            if (maxPadding > 0) {
//                viewPager.setTranslationY(viewPager.getTranslationY() / maxPadding * newMaxPadding);
//            } else {
//                viewPager.setTranslationY(newMaxPadding);
//            }
            maxPadding = newMaxPadding;
            viewPager.setPadding(0, AndroidUtilities.statusBarHeight, 0, 0);
            viewPager.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
            tabsView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0);
            galleryButton.measure(MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(dp(40), MeasureSpec.EXACTLY));
            galleryButton.setTranslationY(-AndroidUtilities.navigationBarHeight);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            backgroundPaint.setAlpha((int) (0xFF * (blurBitmap == null ? .95f : .85f)));
            View[] views = viewPager.getViewPages();
            top = 0;
            if (views[0] instanceof Page) {
                top += ((Page) views[0]).top() * Utilities.clamp(1f - Math.abs(views[0].getTranslationX() / (float) views[0].getMeasuredWidth()), 1, 0);
                if (views[0].getVisibility() == View.VISIBLE) {
                    ((Page) views[0]).updateTops();
                }
            }
            if (views[1] instanceof Page) {
                top += ((Page) views[1]).top() * Utilities.clamp(1f - Math.abs(views[1].getTranslationX() / (float) views[1].getMeasuredWidth()), 1, 0);
                if (views[1].getVisibility() == View.VISIBLE) {
                    ((Page) views[1]).updateTops();
                }
            }
            final float statusBar = isActionBarT.set(top <= 0 ? 1 : 0);
            final float y = top + viewPager.getPaddingTop() - lerp(dp(8), viewPager.getPaddingTop(), statusBar);
            AndroidUtilities.rectTmp.set(backgroundPaddingLeft, y, getWidth() - backgroundPaddingLeft, getHeight() + AndroidUtilities.dp(8));
            if (blurBitmap != null) {
                blurBitmapMatrix.reset();
                blurBitmapMatrix.postScale(12, 12);
                blurBitmapMatrix.postTranslate(0, -getY());
                blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(14), dp(14), backgroundBlurPaint);
            }
            canvas.drawRoundRect(AndroidUtilities.rectTmp, (1f - statusBar) * dp(14), (1f - statusBar) * dp(14), backgroundPaint);
            handleRect.set(
                (getWidth() - dp(36)) / 2f,
                y + dp(9.66f),
                (getWidth() + dp(36)) / 2f,
                y + dp(9.66f + 4)
            );
            handlePaint.setColor(0x51838383);
            handlePaint.setAlpha((int) (0x51 * (1f - statusBar)));
            canvas.drawRoundRect(handleRect, dp(4), dp(4), handlePaint);
            canvas.save();
            canvas.clipRect(AndroidUtilities.rectTmp);
            super.dispatchDraw(canvas);
            canvas.restore();
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
                dismiss();
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    @Override
    public int getContainerViewHeight() {
        if (containerView.getMeasuredHeight() <= 0) {
            return AndroidUtilities.displaySize.y;
        }
        return (int) (containerView.getMeasuredHeight() - viewPager.getY());
    }

    private Utilities.Callback<TLRPC.Document> onDocumentSelected;
    public EmojiBottomSheet whenSelected(Utilities.Callback<TLRPC.Document> listener) {
        this.onDocumentSelected = listener;
        return this;
    }

    @Override
    protected boolean canDismissWithSwipe() {
        return viewPager.getTranslationY() >= (int) maxPadding;
    }

    private static class EmojiListView extends RecyclerListView {
        public static class EmojiImageView extends View {

            public boolean notDraw;
            public boolean emoji;

            private final int currentAccount = UserConfig.selectedAccount;
            private final EmojiListView listView;
            public AnimatedEmojiDrawable drawable;
            public ImageReceiver imageReceiver;
            private long documentId;

            public ImageReceiver.BackgroundThreadDrawHolder[] backgroundThreadDrawHolder = new ImageReceiver.BackgroundThreadDrawHolder[DrawingInBackgroundThreadDrawable.THREAD_COUNT];
            public ImageReceiver imageReceiverToDraw;

            private final ButtonBounce bounce = new ButtonBounce(this);

            public EmojiImageView(Context context, EmojiListView parent) {
                super(context);
                setPadding(AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2), AndroidUtilities.dp(2));
                this.listView = parent;
            }

            public void setEmoji(TLRPC.Document document) {
                if (documentId == (document == null ? 0 : document.id)) {
                    return;
                }
                if (drawable != null) {
                    drawable.removeView(this);
                }
                if (document != null) {
                    emoji = true;
                    documentId = document.id;
                    drawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, document);
                    if (attached) {
                        drawable.addView(this);
                    }
                } else {
                    emoji = false;
                    documentId = 0;
                    drawable = null;
                }
            }

            @Override
            public void invalidate() {
                listView.invalidate();
            }

            public void setEmojiId(long documentId) {
                if (this.documentId == documentId) {
                    return;
                }
                if (drawable != null) {
                    drawable.removeView(this);
                }
                if (documentId != 0) {
                    emoji = true;
                    this.documentId = documentId;
                    drawable = AnimatedEmojiDrawable.make(currentAccount, AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW, documentId);
                    if (attached) {
                        drawable.addView(this);
                    }
                } else {
                    emoji = false;
                    this.documentId = 0;
                    drawable = null;
                }
            }

            @Override
            public void setPressed(boolean pressed) {
                super.setPressed(pressed);
                bounce.setPressed(pressed);
            }

            public float getScale() {
                return bounce.getScale(.15f);
            }

            public void setSticker(TLRPC.Document document) {
                emoji = false;
                if (document != null) {
                    if (documentId == document.id) {
                        return;
                    }
                    documentId = document.id;
                    if (imageReceiver == null) {
                        imageReceiver = new ImageReceiver();
                        imageReceiver.setLayerNum(7);
                        imageReceiver.setAspectFit(true);
                        imageReceiver.setParentView(listView);
                        if (attached) {
                            imageReceiver.onAttachedToWindow();
                        }
                    }
                    SvgHelper.SvgDrawable svgThumb = DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    String filter = "80_80";
                    if ("video/webm".equals(document.mime_type)) {
                        filter += "_" + ImageLoader.AUTOPLAY_FILTER;
                    }
                    if (svgThumb != null) {
                        svgThumb.overrideWidthAndHeight(512, 512);
                    }
                    imageReceiver.setImage(ImageLocation.getForDocument(document), filter, ImageLocation.getForDocument(thumb, document), "80_80", svgThumb, 0, null, document, 0);
                } else if (imageReceiver != null) {
                    documentId = 0;
                    imageReceiver.clearImage();
                }
            }

            boolean attached;

            @Override
            protected void onAttachedToWindow() {
                super.onAttachedToWindow();
                attached = true;
                if (drawable != null) {
                    drawable.addView(listView);
                }
                if (imageReceiver != null) {
                    imageReceiver.onAttachedToWindow();
                }
            }

            @Override
            protected void onDetachedFromWindow() {
                super.onDetachedFromWindow();
                attached = false;
                if (drawable != null) {
                    drawable.removeView(listView);
                }
                if (imageReceiver != null) {
                    imageReceiver.onDetachedFromWindow();
                }
            }

            @Override
            protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
                final int spec = MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY);
                super.onMeasure(spec, spec);
            }

            public void update(long time) {
                if (imageReceiverToDraw != null) {
                    if (imageReceiverToDraw.getLottieAnimation() != null) {
                        imageReceiverToDraw.getLottieAnimation().updateCurrentFrame(time, true);
                    }
                    if (imageReceiverToDraw.getAnimation() != null) {
                        imageReceiverToDraw.getAnimation().updateCurrentFrame(time, true);
                    }
                }
            }

            @Override
            protected void onDraw(Canvas canvas) {
                if (imageReceiver != null) {
                    imageReceiver.setImageCoords(0, 0, getWidth(), getHeight());
                    imageReceiver.draw(canvas);
                } else if (drawable != null) {
                    drawable.setBounds(0, 0, getWidth(), getHeight());
                    drawable.draw(canvas);
                }
            }
        }

        private RecyclerAnimationScrollHelper scrollHelper;
        public boolean emoji;

        public EmojiListView(Context context) {
            super(context);
        }

        @Override
        public void setLayoutManager(@Nullable LayoutManager layout) {
            super.setLayoutManager(layout);

            scrollHelper = null;
            if (layout instanceof LinearLayoutManager) {
                scrollHelper = new RecyclerAnimationScrollHelper(this, (LinearLayoutManager) layout);
                scrollHelper.setAnimationCallback(new RecyclerAnimationScrollHelper.AnimationCallback() {
                    @Override
                    public void onPreAnimation() {
                        smoothScrolling = true;
                    }

                    @Override
                    public void onEndAnimation() {
                        smoothScrolling = false;
                    }
                });
                scrollHelper.setScrollListener(this::invalidate);
            }
        }

        private void scrollToPosition(int position, int offset) {
            if (scrollHelper == null || !(getLayoutManager() instanceof GridLayoutManager)) {
                return;
            }
            GridLayoutManager layoutManager = (GridLayoutManager) getLayoutManager();
            View view = layoutManager.findViewByPosition(position);
            int firstPosition = layoutManager.findFirstVisibleItemPosition();
            if ((view == null && Math.abs(position - firstPosition) > layoutManager.getSpanCount() * 9f) || !SharedConfig.animationsEnabled()) {
                scrollHelper.setScrollDirection(layoutManager.findFirstVisibleItemPosition() < position ? RecyclerAnimationScrollHelper.SCROLL_DIRECTION_DOWN : RecyclerAnimationScrollHelper.SCROLL_DIRECTION_UP);
                scrollHelper.scrollToPosition(position, offset, false, true);
            } else {
                LinearSmoothScrollerCustom linearSmoothScroller = new LinearSmoothScrollerCustom(getContext(), LinearSmoothScrollerCustom.POSITION_TOP) {
                    @Override
                    public void onEnd() {
                        smoothScrolling = false;
                    }
                    @Override
                    protected void onStart() {
                        smoothScrolling = true;
                    }
                };
                linearSmoothScroller.setTargetPosition(position);
                linearSmoothScroller.setOffset(offset);
                layoutManager.startSmoothScroll(linearSmoothScroller);
            }
        }

        private float topBound, bottomBound;
        public void setBounds(float topBound, float bottomBound) {
            this.topBound = topBound;
            this.bottomBound = bottomBound;
        }

        public boolean smoothScrolling = false;

        private final SparseArray<ArrayList<EmojiImageView>> viewsGroupedByLines = new SparseArray<>();
        private final ArrayList<ArrayList<EmojiImageView>> unusedArrays = new ArrayList<>();
        private final ArrayList<DrawingInBackgroundLine> unusedLineDrawables = new ArrayList<>();
        private final ArrayList<DrawingInBackgroundLine> lineDrawables = new ArrayList<>();
        private final ArrayList<DrawingInBackgroundLine> lineDrawablesTmp = new ArrayList<>();
        private boolean invalidated;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            if (getVisibility() != View.VISIBLE) {
                return;
            }
            invalidated = false;
            int restoreTo = canvas.getSaveCount();

            canvas.save();
            canvas.clipRect(0, topBound, getWidth(), bottomBound);

            if (!selectorRect.isEmpty()) {
                selectorDrawable.setBounds(selectorRect);
                canvas.save();
                if (selectorTransformer != null) {
                    selectorTransformer.accept(canvas);
                }
                selectorDrawable.draw(canvas);
                canvas.restore();
            }

            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                ArrayList<EmojiImageView> arrayList = viewsGroupedByLines.valueAt(i);
                arrayList.clear();
                unusedArrays.add(arrayList);
            }
            viewsGroupedByLines.clear();

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child instanceof EmojiImageView) {
                    EmojiImageView view = (EmojiImageView) child;

                    if (view.getY() >= bottomBound || view.getY() + view.getHeight() <= topBound) {
                        continue;
                    }

                    int top = smoothScrolling ? (int) view.getY() : view.getTop();
                    ArrayList<EmojiImageView> arrayList = viewsGroupedByLines.get(top);

                    if (arrayList == null) {
                        if (!unusedArrays.isEmpty()) {
                            arrayList = unusedArrays.remove(unusedArrays.size() - 1);
                        } else {
                            arrayList = new ArrayList<>();
                        }
                        viewsGroupedByLines.put(top, arrayList);
                    }
                    arrayList.add(view);
                }
            }

            lineDrawablesTmp.clear();
            lineDrawablesTmp.addAll(lineDrawables);
            lineDrawables.clear();

            canvas.save();
            canvas.clipRect(0, getPaddingTop(), getWidth(), getHeight() - getPaddingBottom());

            long time = System.currentTimeMillis();
            for (int i = 0; i < viewsGroupedByLines.size(); i++) {
                ArrayList<EmojiImageView> arrayList = viewsGroupedByLines.valueAt(i);
                EmojiImageView firstView = arrayList.get(0);
                int position = getChildAdapterPosition(firstView);
                DrawingInBackgroundLine drawable = null;
                for (int k = 0; k < lineDrawablesTmp.size(); k++) {
                    if (lineDrawablesTmp.get(k).position == position) {
                        drawable = lineDrawablesTmp.get(k);
                        lineDrawablesTmp.remove(k);
                        break;
                    }
                }
                if (drawable == null) {
                    if (!unusedLineDrawables.isEmpty()) {
                        drawable = unusedLineDrawables.remove(unusedLineDrawables.size() - 1);
                    } else {
                        drawable = new DrawingInBackgroundLine();
                        drawable.setLayerNum(7);
                    }
                    drawable.position = position;
                    drawable.onAttachToWindow();
                }
                lineDrawables.add(drawable);
                drawable.imageViewEmojis = arrayList;
                canvas.save();
                canvas.translate(firstView.getLeft(), firstView.getY()/* + firstView.getPaddingTop()*/);
                drawable.startOffset = firstView.getLeft();
                int w = getMeasuredWidth() - firstView.getLeft() * 2;
                int h = firstView.getMeasuredHeight();
                if (w > 0 && h > 0) {
                    drawable.draw(canvas, time, w, h, getAlpha());
                }
                canvas.restore();
            }

            for (int i = 0; i < lineDrawablesTmp.size(); i++) {
                if (unusedLineDrawables.size() < 3) {
                    unusedLineDrawables.add(lineDrawablesTmp.get(i));
                    lineDrawablesTmp.get(i).imageViewEmojis = null;
                    lineDrawablesTmp.get(i).reset();
                } else {
                    lineDrawablesTmp.get(i).onDetachFromWindow();
                }
            }
            lineDrawablesTmp.clear();

            for (int i = 0; i < getChildCount(); ++i) {
                View child = getChildAt(i);
                if (child != null && !(child instanceof EmojiImageView)) {

                    if (child.getY() > getHeight() - getPaddingBottom() || child.getY() + child.getHeight() < getPaddingTop()) {
                        continue;
                    }

                    canvas.save();
                    canvas.translate((int) child.getX(), (int) child.getY());
                    child.draw(canvas);
                    canvas.restore();
                }
            }

            canvas.restore();

            canvas.restoreToCount(restoreTo);
        }

        private final ColorFilter whiteFilter = new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN);

        public class DrawingInBackgroundLine extends DrawingInBackgroundThreadDrawable {

            public int position;
            public int startOffset;
            ArrayList<EmojiImageView> imageViewEmojis;
            ArrayList<EmojiImageView> drawInBackgroundViews = new ArrayList<>();

            boolean lite = LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS);

            @Override
            public void draw(Canvas canvas, long time, int w, int h, float alpha) {
                if (imageViewEmojis == null) {
                    return;
                }
                boolean drawInUi = isAnimating() || imageViewEmojis.size() <= 4 || !lite;
                if (!drawInUi) {
                    for (int i = 0; i < imageViewEmojis.size(); ++i) {
                        if (imageViewEmojis.get(i).getScale() != 1) {
                            drawInUi = true;
                            break;
                        }
                    }
                }
                if (drawInUi) {
                    prepareDraw(System.currentTimeMillis());
                    drawInUiThread(canvas, alpha);
                    reset();
                } else {
                    super.draw(canvas, time, w, h, alpha);
                }
            }

            @Override
            public void prepareDraw(long time) {
                drawInBackgroundViews.clear();
                for (int i = 0; i < imageViewEmojis.size(); i++) {
                    EmojiImageView imageView = imageViewEmojis.get(i);
                    if (imageView.notDraw) {
                        continue;
                    }
                    ImageReceiver imageReceiver = imageView.drawable != null ? imageView.drawable.getImageReceiver() : imageView.imageReceiver;
                    if (imageReceiver == null) {
                        continue;
                    }
                    imageReceiver.setAlpha(imageView.getAlpha());
                    if (imageView.drawable != null) {
                        imageView.drawable.setColorFilter(whiteFilter);
                    }
                    imageView.backgroundThreadDrawHolder[threadIndex] = imageReceiver.setDrawInBackgroundThread(imageView.backgroundThreadDrawHolder[threadIndex], threadIndex);
                    imageView.backgroundThreadDrawHolder[threadIndex].time = time;
                    imageView.imageReceiverToDraw = imageReceiver;

                    imageView.update(time);

                    int topOffset = 0; // (int) (imageView.getHeight() * .03f);
//                    int w = imageView.getWidth() - imageView.getPaddingLeft() - imageView.getPaddingRight();
//                    int h = imageView.getHeight() - imageView.getPaddingTop() - imageView.getPaddingBottom();
                    AndroidUtilities.rectTmp2.set(imageView.getPaddingLeft(), imageView.getPaddingTop(), imageView.getWidth() - imageView.getPaddingRight(), imageView.getHeight() - imageView.getPaddingBottom());
                    if (imageReceiver != null) {
                        final float aspectRatio = getAspectRatio(imageReceiver);
                        if (aspectRatio < 0) {
                            final float w = AndroidUtilities.rectTmp2.height() * aspectRatio;
                            final int left = (int) (AndroidUtilities.rectTmp2.centerX() - w / 2);
                            final int right = (int) (AndroidUtilities.rectTmp2.centerX() + w / 2);
                            AndroidUtilities.rectTmp2.left = left;
                            AndroidUtilities.rectTmp2.right = right;
                        } else if (aspectRatio > 1) {
                            final float h = AndroidUtilities.rectTmp2.width() / aspectRatio;
                            final int top = (int) (AndroidUtilities.rectTmp2.centerY() - h / 2);
                            final int bottom = (int) (AndroidUtilities.rectTmp2.centerY() + h / 2);
                            AndroidUtilities.rectTmp2.top = top;
                            AndroidUtilities.rectTmp2.bottom = bottom;
                        }
                    }
                    AndroidUtilities.rectTmp2.offset(imageView.getLeft() + (int) imageView.getTranslationX() - startOffset, topOffset);
                    imageView.backgroundThreadDrawHolder[threadIndex].setBounds(AndroidUtilities.rectTmp2);

                    drawInBackgroundViews.add(imageView);
                }
            }

            private float getAspectRatio(ImageReceiver imageReceiver) {
                if (imageReceiver == null) {
                    return 1f;
                }
                RLottieDrawable rLottieDrawable = imageReceiver.getLottieAnimation();
                if (rLottieDrawable != null && rLottieDrawable.getIntrinsicHeight() != 0) {
                    return (float) rLottieDrawable.getIntrinsicWidth() / rLottieDrawable.getIntrinsicHeight();
                }
                AnimatedFileDrawable animatedDrawable = imageReceiver.getAnimation();
                if (animatedDrawable != null && animatedDrawable.getIntrinsicHeight() != 0) {
                    return (float) animatedDrawable.getIntrinsicWidth() / (float) animatedDrawable.getIntrinsicHeight();
                }
                Bitmap bitmap = imageReceiver.getBitmap();
                if (bitmap != null) {
                    return (float) bitmap.getWidth() / (float) bitmap.getHeight();
                }
                Drawable thumbDrawable = imageReceiver.getStaticThumb();
                if (thumbDrawable != null && thumbDrawable.getIntrinsicHeight() != 0) {
                    return (float) thumbDrawable.getIntrinsicWidth() / (float) thumbDrawable.getIntrinsicHeight();
                }
                return 1f;
            }

            @Override
            public void drawInBackground(Canvas canvas) {
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    EmojiImageView imageView = drawInBackgroundViews.get(i);
                    if (!imageView.notDraw) {
                        if (imageView.drawable != null) {
                            imageView.drawable.setColorFilter(whiteFilter);
                        }
                        imageView.imageReceiverToDraw.draw(canvas, imageView.backgroundThreadDrawHolder[threadIndex]);
                    }
                }
            }

            @Override
            protected void drawInUiThread(Canvas canvas, float palpha) {
                if (imageViewEmojis != null) {
                    canvas.save();
                    canvas.translate(-startOffset, 0);
                    for (int i = 0; i < imageViewEmojis.size(); i++) {
                        EmojiImageView imageView = imageViewEmojis.get(i);
                        if (imageView.notDraw) {
                            continue;
                        }

                        float scale = imageView.getScale();
                        float alpha = palpha * imageView.getAlpha();

                        AndroidUtilities.rectTmp2.set((int) imageView.getX() + imageView.getPaddingLeft(), imageView.getPaddingTop(), (int) imageView.getX() + imageView.getWidth() - imageView.getPaddingRight(), imageView.getHeight() - imageView.getPaddingBottom());
//                        if (!smoothScrolling && !animatedExpandIn) {
//                            AndroidUtilities.rectTmp2.offset(0, (int) imageView.getTranslationY());
//                        }
                        Drawable drawable = imageView.drawable;
                        if (drawable != null) {
                            drawable.setBounds(AndroidUtilities.rectTmp2);
                        }
                        if (imageView.imageReceiver != null) {
                            imageView.imageReceiver.setImageCoords(AndroidUtilities.rectTmp2);
                        }
                        if (whiteFilter != null && imageView.drawable instanceof AnimatedEmojiDrawable) {
                            imageView.drawable.setColorFilter(whiteFilter);
                        }
                        if (scale != 1) {
                            canvas.save();
                            canvas.scale(scale, scale, AndroidUtilities.rectTmp2.centerX(), AndroidUtilities.rectTmp2.centerY());
                            drawImage(canvas, drawable, imageView, alpha);
                            canvas.restore();
                        } else {
                            drawImage(canvas, drawable, imageView, alpha);
                        }
                    }
                    canvas.restore();
                }
            }

            private void drawImage(Canvas canvas, Drawable drawable, EmojiImageView imageView, float alpha) {
                if (drawable != null) {
                    drawable.setAlpha((int) (255 * alpha));
                    drawable.draw(canvas);
//                    drawable.setColorFilter(premiumStarColorFilter);
                } else if (imageView.imageReceiver != null) {
                    canvas.save();
                    canvas.clipRect(imageView.imageReceiver.getImageX(), imageView.imageReceiver.getImageY(), imageView.imageReceiver.getImageX2(), imageView.imageReceiver.getImageY2());
                    imageView.imageReceiver.setAlpha(alpha);
                    imageView.imageReceiver.draw(canvas);
                    canvas.restore();
                }
            }

            @Override
            public void onFrameReady() {
                super.onFrameReady();
                for (int i = 0; i < drawInBackgroundViews.size(); i++) {
                    EmojiImageView imageView = drawInBackgroundViews.get(i);
                    if (imageView.backgroundThreadDrawHolder[threadIndex] != null) {
                        imageView.backgroundThreadDrawHolder[threadIndex].release();
                    }
                }
                EmojiListView.this.invalidate();
            }
        }

    }

    private static class SearchField extends FrameLayout {

        private final Theme.ResourcesProvider resourcesProvider;

        private final FrameLayout box;

        private final ImageView searchImageView;
        private final SearchStateDrawable searchImageDrawable;

        private final FrameLayout inputBox;
        private final EditTextBoldCursor editText;
        private final StickerCategoriesListView categoriesListView;

        public boolean ignoreTextChange;

        public SearchField(Context context, Theme.ResourcesProvider resourcesProvider) {
            super(context);
            this.resourcesProvider = resourcesProvider;

            box = new FrameLayout(context);
            box.setBackground(Theme.createRoundRectDrawable(dp(18), Theme.getColor(Theme.key_chat_emojiSearchBackground, resourcesProvider)));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                box.setClipToOutline(true);
                box.setOutlineProvider(new ViewOutlineProvider() {
                    @Override
                    public void getOutline(View view, Outline outline) {
                        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), (int) dp(18));
                    }
                });
            }
            addView(box, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.FILL, 10, 6, 10, 8));

            inputBox = new FrameLayout(context);
            box.addView(inputBox, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 38, 0, 0, 0));

            searchImageView = new ImageView(context);
            searchImageView.setScaleType(ImageView.ScaleType.CENTER);
            searchImageDrawable = new SearchStateDrawable();
            searchImageDrawable.setIconState(SearchStateDrawable.State.STATE_SEARCH, false);
            searchImageDrawable.setColor(Theme.getColor(Theme.key_chat_emojiSearchIcon, resourcesProvider));
            searchImageView.setImageDrawable(searchImageDrawable);
            box.addView(searchImageView, LayoutHelper.createFrame(36, 36, Gravity.LEFT | Gravity.TOP));

            editText = new EditTextBoldCursor(context) {
                @Override
                public boolean onTouchEvent(MotionEvent event) {
                    if (!editText.isEnabled()) {
                        return super.onTouchEvent(event);
                    }
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        // todo: open search
                        editText.requestFocus();
                        AndroidUtilities.showKeyboard(editText);
                    }
                    return super.onTouchEvent(event);
                }
            };
            editText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            editText.setHintTextColor(Theme.getColor(Theme.key_chat_emojiSearchIcon, resourcesProvider));
            editText.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider));
            editText.setBackgroundDrawable(null);
            editText.setPadding(0, 0, 0, 0);
            editText.setMaxLines(1);
            editText.setLines(1);
            editText.setSingleLine(true);
            editText.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
            editText.setHint(LocaleController.getString("Search", R.string.Search));
            editText.setCursorColor(Theme.getColor(Theme.key_featuredStickers_addedIcon, resourcesProvider));
            editText.setHandlesColor(Theme.getColor(Theme.key_featuredStickers_addedIcon, resourcesProvider));
            editText.setCursorSize(dp(20));
            editText.setCursorWidth(1.5f);
            editText.setTranslationY(dp(-2));
            inputBox.addView(editText, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.TOP, 0, 0, 28, 0));
            editText.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {}
                @Override
                public void afterTextChanged(Editable s) {
                    if (ignoreTextChange) {
                        return;
                    }
                    updateButton();
                    final String query = editText.getText().toString();
                    search(TextUtils.isEmpty(query) ? null : query, -1);
                    if (categoriesListView != null) {
                        categoriesListView.selectCategory(null);
                        categoriesListView.updateCategoriesShown(TextUtils.isEmpty(query), true);
                    }
                    if (editText != null) {
                        editText.animate().cancel();
                        editText.animate().translationX(0).setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT).start();
                    }
                }
            });

            categoriesListView = new StickerCategoriesListView(context, null, StickerCategoriesListView.CategoriesType.DEFAULT, resourcesProvider) {
                @Override
                public void selectCategory(int categoryIndex) {
                    super.selectCategory(categoryIndex);
//                    if (type == 1 && emojiTabs != null) {
//                        emojiTabs.showSelected(categoriesListView.getSelectedCategory() == null);
//                    } else if (type == 0 && stickersTab != null) {
//                        stickersTab.showSelected(categoriesListView.getSelectedCategory() == null);
//                    }
                    updateButton();
                }

                @Override
                protected boolean isTabIconsAnimationEnabled(boolean loaded) {
                    return LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_EMOJI_REACTIONS);
                }
            };
            categoriesListView.setDontOccupyWidth((int) (editText.getPaint().measureText(editText.getHint() + "")) + dp(16));
            categoriesListView.setOnScrollIntoOccupiedWidth(scrolled -> {
                editText.animate().cancel();
                editText.setTranslationX(-Math.max(0, scrolled));
//                showInputBoxGradient(scrolled > 0);
                updateButton();
            });
//            categoriesListView.setOnTouchListener(new OnTouchListener() {
//                @Override
//                public boolean onTouch(View v, MotionEvent event) {
////                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
////                        ignorePagerScroll = true;
////                    } else if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
////                        ignorePagerScroll = false;
////                    }
//                    return false;
//                }
//            });
            categoriesListView.setOnCategoryClick(category -> {
//                if (category == recent) {
////                    showInputBoxGradient(false);
//                    categoriesListView.selectCategory(recent);
//                    gifSearchField.searchEditText.setText("");
//                    gifLayoutManager.scrollToPositionWithOffset(0, 0);
//                    return;
//                } else
//                if (category == trending) {
////                    showInputBoxGradient(false);
//                    gifSearchField.searchEditText.setText("");
//                    gifLayoutManager.scrollToPositionWithOffset(gifAdapter.trendingSectionItem, -dp(4));
//                    categoriesListView.selectCategory(trending);
//                    final ArrayList<String> gifSearchEmojies = MessagesController.getInstance(currentAccount).gifSearchEmojies;
//                    if (!gifSearchEmojies.isEmpty()) {
//                        gifSearchPreloader.preload(gifSearchEmojies.get(0));
//                    }
//                    return;
//                }
                if (categoriesListView.getSelectedCategory() == category) {
                    categoriesListView.selectCategory(null);
                    search(null, -1);
                } else {
                    categoriesListView.selectCategory(category);
                    search(category.emojis, categoriesListView.getCategoryIndex());
                }
            });
            box.addView(categoriesListView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 36, 0, 0, 0));

            searchImageView.setOnClickListener(e -> {
                if (searchImageDrawable.getIconState() == SearchStateDrawable.State.STATE_BACK) {
                    clear();
                    categoriesListView.scrollToStart();
                } else if (searchImageDrawable.getIconState() == SearchStateDrawable.State.STATE_SEARCH) {
                    editText.requestFocus();
                }
            });
        }

        private void updateButton() {
            updateButton(false);
        }

        private boolean isprogress;
        public void showProgress(boolean progress) {
            isprogress = progress;
            if (progress) {
                searchImageDrawable.setIconState(SearchStateDrawable.State.STATE_PROGRESS);
            } else {
                updateButton(true);
            }
        }

        private void updateButton(boolean force) {
            if (!isprogress || editText.length() == 0 && (categoriesListView == null || categoriesListView.getSelectedCategory() == null) || force) {
                boolean backButton = editText.length() > 0 || categoriesListView != null && categoriesListView.isCategoriesShown() && (categoriesListView.isScrolledIntoOccupiedWidth() || categoriesListView.getSelectedCategory() != null);
                searchImageDrawable.setIconState(backButton ? SearchStateDrawable.State.STATE_BACK : SearchStateDrawable.State.STATE_SEARCH);
                isprogress = false;
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(50), MeasureSpec.EXACTLY)
            );
        }

        private Utilities.Callback2<String, Integer> onSearchQuery;
        public void setOnSearchQuery(Utilities.Callback2<String, Integer> onSearchQuery) {
            this.onSearchQuery = onSearchQuery;
        }

        private void search(String query, int categoryIndex) {
            if (onSearchQuery != null) {
                onSearchQuery.run(query, categoryIndex);
            }
        }

        private void clear() {
            editText.setText("");
            search(null, -1);
            categoriesListView.selectCategory(null);
        }
    }

    private static class TabsView extends View {

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private StaticLayout emojiLayout;
        private float emojiLayoutWidth, emojiLayoutLeft;
        private StaticLayout stickersLayout;
        private float stickersLayoutWidth, stickersLayoutLeft;
        private StaticLayout masksLayout;
        private float masksLayoutWidth, masksLayoutLeft;

        private final RectF emojiRect = new RectF();
        private final RectF stickersRect = new RectF();
        private final RectF masksRect = new RectF();
        private final RectF selectRect = new RectF();

        public TabsView(Context context) {
            super(context);
        }

        private float type;
        public void setType(float type) {
            this.type = type;
            invalidate();
        }

        private Utilities.Callback<Integer> onTypeSelected;
        public void setOnTypeSelected(Utilities.Callback<Integer> listener) {
            onTypeSelected = listener;
        }

        private int lastWidth;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            final int width = MeasureSpec.getSize(widthMeasureSpec);
            setMeasuredDimension(width, dp(40) + AndroidUtilities.navigationBarHeight);
            if (getMeasuredWidth() != lastWidth || emojiLayout == null) {
                updateLayouts();
            }
            lastWidth = getMeasuredWidth();
        }

        private void updateLayouts() {
            textPaint.setTextSize(dp(14));
            textPaint.setTypeface(AndroidUtilities.getTypeface(AndroidUtilities.TYPEFACE_ROBOTO_MEDIUM));

            emojiLayout = new StaticLayout(LocaleController.getString("Emoji"), textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            emojiLayoutWidth = emojiLayout.getLineCount() >= 1 ? emojiLayout.getLineWidth(0) : 0;
            emojiLayoutLeft = emojiLayout.getLineCount() >= 1 ? emojiLayout.getLineLeft(0) : 0;

            stickersLayout = new StaticLayout(LocaleController.getString("AccDescrStickers"), textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            stickersLayoutWidth = stickersLayout.getLineCount() >= 1 ? stickersLayout.getLineWidth(0) : 0;
            stickersLayoutLeft = stickersLayout.getLineCount() >= 1 ? stickersLayout.getLineLeft(0) : 0;

//            masksLayout = new StaticLayout(LocaleController.getString("Masks"), textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
//            masksLayoutWidth = masksLayout.getLineCount() >= 1 ? masksLayout.getLineWidth(0) : 0;
//            masksLayoutLeft = masksLayout.getLineCount() >= 1 ? masksLayout.getLineLeft(0) : 0;
//
//            float w = dp(12) + emojiLayoutWidth + dp(12 + 12 + 12) + stickersLayoutWidth + dp(12 + 12 + 12) + masksLayoutWidth + dp(12);
            float w = dp(12) + emojiLayoutWidth + dp(12 + 12 + 12) + stickersLayoutWidth + dp(12);
            float t = dp(40 - 26) / 2f, b = dp(40 + 26) / 2f;
            float l = (getMeasuredWidth() - w) / 2f;

            emojiRect.set(l, t, l + emojiLayoutWidth + dp(12 + 12), b);
            l += emojiLayoutWidth + dp(12 + 12 + 12);

            stickersRect.set(l, t, l + stickersLayoutWidth + dp(12 + 12), b);
            l += stickersLayoutWidth + dp(12 + 12 + 12);

//            masksLayout.set(l, t, l + masksLayoutWidth + dp(12 + 12), b);
//            l += masksLayoutWidth + dp(12 + 12 + 12);
        }

        private RectF getRect(int t) {
            if (t <= 0) {
                return emojiRect;
            } else { // if (t == 1)
                return stickersRect;
            }
//            else {
//                return masksRect;
//            }
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            canvas.drawColor(0xFF1F1F1F);
            selectPaint.setColor(0xFF363636);

            lerp(getRect((int) type), getRect((int) Math.ceil(type)), type - (int) type, selectRect);
            canvas.drawRoundRect(selectRect, dp(20), dp(20), selectPaint);

            if (emojiLayout != null) {
                canvas.save();
                canvas.translate(emojiRect.left + dp(12) - emojiLayoutLeft, emojiRect.top + (emojiRect.height() - emojiLayout.getHeight()) / 2f);
                textPaint.setColor(ColorUtils.blendARGB(0xFF838383, 0xFFFFFFFF, Utilities.clamp(1f - Math.abs(type - 0), 1, 0)));
                emojiLayout.draw(canvas);
                canvas.restore();
            }

            if (stickersLayout != null) {
                canvas.save();
                canvas.translate(stickersRect.left + dp(12) - stickersLayoutLeft, stickersRect.top + (stickersRect.height() - stickersLayout.getHeight()) / 2f);
                textPaint.setColor(ColorUtils.blendARGB(0xFF838383, 0xFFFFFFFF, Utilities.clamp(1f - Math.abs(type - 1), 1, 0)));
                stickersLayout.draw(canvas);
                canvas.restore();
            }

            if (masksLayout != null) {
                canvas.save();
                canvas.translate(masksRect.left + dp(12) - masksLayoutLeft, masksRect.top + (masksRect.height() - masksLayout.getHeight()) / 2f);
                textPaint.setColor(ColorUtils.blendARGB(0xFF838383, 0xFFFFFFFF, Utilities.clamp(1f - Math.abs(type - 2), 1, 0)));
                masksLayout.draw(canvas);
                canvas.restore();
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                return true;
            } else if (event.getAction() == MotionEvent.ACTION_UP && onTypeSelected != null) {
                if (emojiRect.contains(event.getX(), event.getY())) {
                    onTypeSelected.run(0);
                } else if (stickersRect.contains(event.getX(), event.getY())) {
                    onTypeSelected.run(1);
                }
                return true;
            }
            return super.onTouchEvent(event);
        }
    }

    private static class NoEmojiView extends FrameLayout {

        BackupImageView imageView;
        TextView textView;

        public NoEmojiView(Context context, boolean emoji) {
            super(context);

            imageView = new BackupImageView(context);
            addView(imageView, LayoutHelper.createFrame(36, 36, Gravity.CENTER));

            textView = new TextView(context);
            textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 14);
            textView.setTextColor(-8553090); // Theme.getColor(Theme.key_chat_emojiPanelEmptyText, resourcesProvider)
            textView.setText(emoji ? LocaleController.getString("NoEmojiFound", R.string.NoEmojiFound) : LocaleController.getString("NoStickersFound", R.string.NoStickersFound));
            addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 18 + 16, 0, 0));
        }

        private int lastI = -1;
        public void update(int i) {
            if (lastI != i) {
                lastI = i;
                update();
            }
        }

        public void update() {
            SelectAnimatedEmojiDialog.updateSearchEmptyViewImage(UserConfig.selectedAccount, imageView);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(
                MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec((int) Math.max(AndroidUtilities.dp(170), AndroidUtilities.displaySize.y * (1f - .35f - .3f) - dp(50 + 16 + 36 + 40)), MeasureSpec.EXACTLY)
            );
        }
    }
}
