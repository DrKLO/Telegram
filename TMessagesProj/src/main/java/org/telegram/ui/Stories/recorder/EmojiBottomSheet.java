package org.telegram.ui.Stories.recorder;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.AndroidUtilities.translitSafe;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
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
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.SparseArray;
import android.util.SparseIntArray;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.inputmethod.EditorInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.LinearSmoothScrollerCustom;
import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.LiteMode;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaDataController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.MessagesStorage;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.messenger.SvgHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AdjustPanLayoutHelper;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.BottomSheet;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ContextLinkCell;
import org.telegram.ui.Cells.StickerSetNameCell;
import org.telegram.ui.Components.AnimatedEmojiDrawable;
import org.telegram.ui.Components.AnimatedFileDrawable;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.Bulletin;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.CloseProgressDrawable2;
import org.telegram.ui.Components.CombinedDrawable;
import org.telegram.ui.Components.CubicBezierInterpolator;
import org.telegram.ui.Components.DrawingInBackgroundThreadDrawable;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.EmojiTabsStrip;
import org.telegram.ui.Components.EmojiView;
import org.telegram.ui.Components.ExtendedGridLayoutManager;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LoadingSpan;
import org.telegram.ui.Components.PermissionRequest;
import org.telegram.ui.Components.Premium.PremiumFeatureBottomSheet;
import org.telegram.ui.Components.RLottieDrawable;
import org.telegram.ui.Components.Reactions.ReactionImageHolder;
import org.telegram.ui.Components.Reactions.ReactionsLayoutInBubble;
import org.telegram.ui.Components.RecyclerAnimationScrollHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.SearchStateDrawable;
import org.telegram.ui.Components.Size;
import org.telegram.ui.Components.StickerCategoriesListView;
import org.telegram.ui.Components.ViewPagerFixed;
import org.telegram.ui.ContentPreviewViewer;
import org.telegram.ui.LaunchActivity;
import org.telegram.ui.PremiumPreviewFragment;
import org.telegram.ui.SelectAnimatedEmojiDialog;
import org.telegram.ui.Stories.StoryReactionWidgetBackground;
import org.telegram.ui.WrappedResourceProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

public class EmojiBottomSheet extends BottomSheet implements NotificationCenter.NotificationCenterDelegate {

    private static final int PAGE_TYPE_EMOJI = 0;
    private static final int PAGE_TYPE_STICKERS = 1;
    private static final int PAGE_TYPE_GIFS = 2;

    private String query = null;
    private int categoryIndex = -1;

    public final TLRPC.Document widgets = new TLRPC.Document() {};
    public final TLRPC.Document plus = new TLRPC.Document() {};

    abstract class IPage extends FrameLayout {
        public int currentType;

        public IPage(Context context) {
            super(context);
        }

        public float top() {
            return 0;
        }

        public void updateTops() {

        }

        public void bind(int type) {

        }
    }

    private class GifPage extends IPage implements NotificationCenter.NotificationCenterDelegate {

        public RecyclerListView listView;
        public GifAdapter adapter;
        public SearchField searchField;
        public ExtendedGridLayoutManager layoutManager;

        public GifPage(Context context) {
            super(context);

            listView = new RecyclerListView(context) {
                @Override
                public boolean onInterceptTouchEvent(MotionEvent event) {
                    final boolean result = ContentPreviewViewer.getInstance().onInterceptTouchEvent(event, listView, 0, previewDelegate, resourcesProvider);
                    return super.onInterceptTouchEvent(event) || result;
                }
            };
            listView.setAdapter(adapter = new GifAdapter());
            listView.setLayoutManager(layoutManager = new GifLayoutManager(context));
            listView.addItemDecoration(new RecyclerView.ItemDecoration() {
                @Override
                public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
                    outRect.right = layoutManager.isLastInRow(parent.getChildAdapterPosition(view)) ? 0 : dp(4);
                    outRect.bottom = dp(4);
                }
            });
            listView.setClipToPadding(true);
            listView.setVerticalScrollBarEnabled(false);
            final RecyclerListView.OnItemClickListener onItemClickListener = (view, position) -> {
                Object obj = adapter.getItem(position);
                TLObject res = null;
                TLRPC.Document document = null;
                if (obj instanceof TLRPC.BotInlineResult) {
                    res = (TLRPC.BotInlineResult) obj;
                    document = ((TLRPC.BotInlineResult) obj).document;
                } else if (obj instanceof TLRPC.Document) {
                    document = (TLRPC.Document) obj;
                } else {
                    return;
                }
                if (onDocumentSelected != null) {
                    onDocumentSelected.run(res, document, true);
                }
                dismiss();
            };
            listView.setOnTouchListener((v, event) -> ContentPreviewViewer.getInstance().onTouch(event, listView, 0, onItemClickListener, previewDelegate, resourcesProvider));
            listView.setOnItemClickListener(onItemClickListener);
            listView.setOnScrollListener(new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                    containerView.invalidate();
                    if (keyboardVisible && listView.scrollingByUser && searchField != null && searchField.editText != null) {
                        closeKeyboard();
                    }

                    int position = layoutManager.findLastCompletelyVisibleItemPosition();
                    if (position + 7 >= adapter.getItemCount() - 1) {
                        adapter.request();
                    }
                }
            });
            addView(listView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.FILL, 0, 58, 0, 40));

            searchField = new SearchField(context, resourcesProvider);
            searchField.setOnSearchQuery((query, category) -> {
                EmojiBottomSheet.this.query = query;
                EmojiBottomSheet.this.categoryIndex = category;
                adapter.updateItems(query);
            });
            searchField.checkCategoriesView(PAGE_TYPE_GIFS, false);
            addView(searchField, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.TOP));
        }

        private ContentPreviewViewer.ContentPreviewViewerDelegate previewDelegate = new ContentPreviewViewer.ContentPreviewViewerDelegate() {
            @Override
            public void openSet(TLRPC.InputStickerSet set, boolean clearInputField) {

            }

            @Override
            public boolean needSend(int contentType) {
                return false;
            }

            @Override
            public boolean canSchedule() {
                return false;
            }

            @Override
            public boolean isInScheduleMode() {
                return false;
            }

            @Override
            public long getDialogId() {
                return 0;
            }

            @Override
            public boolean isPhotoEditor() {
                return true;
            }
        };

        @Override
        public float top() {
            for (int i = 0; i < listView.getChildCount(); ++i) {
                View child = listView.getChildAt(i);
                Object tag = child.getTag();
                if (tag instanceof Integer && (int) tag == 34) {
                    return Math.max(0, child.getBottom());
                }
            }
            return 0;
        }

        @Override
        public void updateTops() {
            float top = Math.max(0, top());
//            tabsStrip.setTranslationY(dp(16) + top);
            searchField.setTranslationY(dp(10) + top);
//            listView.setBounds(top + listView.getPaddingTop(), listView.getHeight() - listView.getPaddingBottom());
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setPadding(backgroundPaddingLeft, 0, backgroundPaddingLeft, AndroidUtilities.navigationBarHeight);
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        @Override
        public void bind(int type) {
            adapter.updateRecent(false);
            if (gifs.isEmpty() && TextUtils.isEmpty(query)) {
                adapter.request();
            }
            adapter.updateItems(null);
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.recentDocumentsDidLoad);
        }

        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.recentDocumentsDidLoad);
        }

        @Override
        public void didReceivedNotification(int id, int account, Object... args) {
            if (id == NotificationCenter.recentDocumentsDidLoad) {
                adapter.updateRecent(true);
            }
        }

        private static final int VIEW_TYPE_PAD = 0;
        private static final int VIEW_TYPE_HEADER = 1;
        private static final int VIEW_TYPE_GIF = 2;

        private final ArrayList<TLRPC.Document> mygifs = new ArrayList<>();
        private final ArrayList<TLRPC.BotInlineResult> gifs = new ArrayList<>();

        private class GifAdapter extends RecyclerListView.SelectionAdapter {

            @NonNull
            @Override
            public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View view;
                if (viewType == VIEW_TYPE_PAD) {
                    view = new View(getContext());
                } else if (viewType == VIEW_TYPE_HEADER) {
                    final StickerSetNameCell cell1 = new StickerSetNameCell(getContext(), false, resourcesProvider);
                    cell1.setText(LocaleController.getString(R.string.FeaturedGifs), 0);
                    view = cell1;
                    final RecyclerView.LayoutParams lp = new RecyclerView.LayoutParams(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT);
                    lp.topMargin = AndroidUtilities.dp(2.5f);
                    lp.bottomMargin = AndroidUtilities.dp(5.5f);
                    view.setLayoutParams(lp);
                } else {
                    ContextLinkCell cell = new ContextLinkCell(getContext());
                    cell.getPhotoImage().setLayerNum(7);
                    cell.allowButtonBounce(true);
                    cell.setIsKeyboard(true);
                    cell.setCanPreviewGif(true);
                    view = cell;
                }
                return new RecyclerListView.Holder(view);
            }

            @Override
            public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
                final int viewType = holder.getItemViewType();
                if (viewType == VIEW_TYPE_PAD) {
                    holder.itemView.setTag(34);
                    holder.itemView.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (int) maxPadding));
                } else if (viewType == VIEW_TYPE_GIF) {
                    final ContextLinkCell cell = (ContextLinkCell) holder.itemView;
                    final Object obj = getItem(position);
                    if (obj instanceof TLRPC.Document) {
                        cell.setGif((TLRPC.Document) obj, false);
                    } else if (obj instanceof TLRPC.BotInlineResult) {
                        cell.setLink((TLRPC.BotInlineResult) obj, bot, true, false, false, true);
                    }
                }
            }

            @Override
            public int getItemCount() {
                return 1 + (!mygifs.isEmpty() && TextUtils.isEmpty(query) ? mygifs.size() : 0) + (gifs.isEmpty() ? 0 : (!mygifs.isEmpty() && TextUtils.isEmpty(query) ? 1 : 0) + gifs.size());
            }

            public Object getItem(int position) {
                position--;
                if (!mygifs.isEmpty() && TextUtils.isEmpty(query)) {
                    if (position >= 0 && position < mygifs.size()) {
                        return mygifs.get(position);
                    }
                    position -= mygifs.size();
                }
                if (!gifs.isEmpty()) {
                    if (!mygifs.isEmpty() && TextUtils.isEmpty(query)) {
                        position--;
                    }
                    if (position >= 0 && position < gifs.size()) {
                        return gifs.get(position);
                    }
                }
                return null;
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0) {
                    return VIEW_TYPE_PAD;
                }
                position--;
                if (!mygifs.isEmpty() && TextUtils.isEmpty(query)) {
                    position -= mygifs.size();
                }
                if (!gifs.isEmpty()) {
                    if (!mygifs.isEmpty() && TextUtils.isEmpty(query) && position == 0)
                        return VIEW_TYPE_HEADER;
                    position--;
                }
                return VIEW_TYPE_GIF;
            }

            @Override
            public boolean isEnabled(RecyclerView.ViewHolder holder) {
                return holder.getItemViewType() == VIEW_TYPE_GIF;
            }

            public void updateItems(String query) {
                if (!TextUtils.equals(this.query, query)) {
                    if (currentReqId != -1) {
                        ConnectionsManager.getInstance(currentAccount).cancelRequest(currentReqId, true);
                        currentReqId = -1;
                    }
                    requesting = false;
                    offset = "";
                }
                final boolean wasQueryEmpty = TextUtils.isEmpty(this.query);
                this.query = query;
                AndroidUtilities.cancelRunOnUIThread(searchRunnable);
                if (TextUtils.isEmpty(query)) {
                    gifs.clear();
                    searchField.showProgress(false);
                    notifyDataSetChanged();
                } else {
                    if (wasQueryEmpty) {
                        notifyDataSetChanged();
                    }
                    searchField.showProgress(true);
                    AndroidUtilities.runOnUIThread(searchRunnable, 1500);
                }
            }

            private Runnable searchRunnable = this::request;

            private int currentReqId = -1;
            private String query;
            private TLRPC.User bot;
            private String offset;
            private boolean requestedBot;
            private boolean requesting = false;

            private void updateRecent(boolean notify) {
                mygifs.clear();
                mygifs.addAll(MediaDataController.getInstance(currentAccount).getRecentGifs());
                if (notify) notifyDataSetChanged();
            }

            private void request() {
                if (requesting) {
                    return;
                }
                requesting = true;
                searchField.showProgress(true);

                if (currentReqId >= 0) {
                    ConnectionsManager.getInstance(currentAccount).cancelRequest(currentReqId, true);
                    currentReqId = -1;
                }

                if (bot == null) {
                    TLObject object = MessagesController.getInstance(currentAccount).getUserOrChat(
                        MessagesController.getInstance(currentAccount).gifSearchBot
                    );
                    if (object instanceof TLRPC.User) {
                        bot = (TLRPC.User) object;
                    }
                }
                if (bot == null && !requestedBot) {
                    TLRPC.TL_contacts_resolveUsername req = new TLRPC.TL_contacts_resolveUsername();
                    req.username = MessagesController.getInstance(currentAccount).gifSearchBot;
                    currentReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                        if (res instanceof TLRPC.TL_contacts_resolvedPeer) {
                            TLRPC.TL_contacts_resolvedPeer response = (TLRPC.TL_contacts_resolvedPeer) res;
                            MessagesController.getInstance(currentAccount).putUsers(response.users, false);
                            MessagesController.getInstance(currentAccount).putChats(response.chats, false);
                            MessagesStorage.getInstance(currentAccount).putUsersAndChats(response.users, response.chats, true, true);
                        }
                        requestedBot = true;
                        request();
                    }));
                    return;
                }
                if (bot == null) {
                    return;
                }

                TLRPC.TL_messages_getInlineBotResults req = new TLRPC.TL_messages_getInlineBotResults();
                req.bot = MessagesController.getInstance(currentAccount).getInputUser(bot);
                req.query = query == null ? "" : query;
                final boolean emptyOffset = TextUtils.isEmpty(offset);
                req.offset = offset == null ? "" : offset;
                req.peer = new TLRPC.TL_inputPeerEmpty();

                final String key = "gif_search_" + req.query + "_" + req.offset;
                MessagesStorage.getInstance(currentAccount).getBotCache(key, (res, err) -> AndroidUtilities.runOnUIThread(() -> {
                    if (!requesting) {
                        return;
                    }
                    if (res instanceof TLRPC.messages_BotResults) {
                        TLRPC.messages_BotResults response = (TLRPC.messages_BotResults) res;
                        offset = response.next_offset;

                        if (emptyOffset) {
                            gifs.clear();
                        }
                        int position = gifs.size();
                        gifs.addAll(response.results);
                        notifyDataSetChanged();

                        searchField.showProgress(false);
                        requesting = false;
                    } else {
                        currentReqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (res2, err2) -> AndroidUtilities.runOnUIThread(() -> {
                            if (!requesting) {
                                return;
                            }
                            if (res2 instanceof TLRPC.messages_BotResults) {
                                TLRPC.messages_BotResults response = (TLRPC.messages_BotResults) res2;
                                MessagesStorage.getInstance(currentAccount).saveBotCache(key, response);
                                offset = response.next_offset;

                                if (emptyOffset) {
                                    gifs.clear();
                                }
                                int position = gifs.size();
                                gifs.addAll(response.results);
                                notifyDataSetChanged();
                            }

                            searchField.showProgress(false);
                            requesting = false;
                        }));
                    }
                }));
            }
        }

        private class GifLayoutManager extends ExtendedGridLayoutManager {

            private final Size size = new Size();

            public GifLayoutManager(Context context) {
                super(context, 100, true);
                setSpanSizeLookup(new GridLayoutManager.SpanSizeLookup() {
                    @Override
                    public int getSpanSize(int position) {
                        Object obj = adapter.getItem(position);
                        if (obj == null) return getSpanCount();
                        return getSpanSizeForItem(position);
                    }
                });
            }

            @Override
            protected int getFlowItemCount() {
                return getItemCount();
            }


            @Override
            protected Size getSizeForItem(int i) {
                size.full = false;
                TLRPC.Document document = null;
                ArrayList<TLRPC.DocumentAttribute> attributes = null;
                Object obj = adapter.getItem(i);
                if (obj instanceof TLRPC.BotInlineResult) {
                    TLRPC.BotInlineResult result = (TLRPC.BotInlineResult) obj;
                    document = result.document;
                    if (document != null) {
                        attributes = document.attributes;
                    } else if (result.content != null) {
                        attributes = result.content.attributes;
                    } else if (result.thumb != null) {
                        attributes = result.thumb.attributes;
                    } else {
                        attributes = null;
                    }
                } else if (obj instanceof TLRPC.Document) {
                    document = (TLRPC.Document) obj;
                    attributes = document.attributes;
                } else {
                    size.full = true;
                    return size;
                }
                return getSizeForItem(document, attributes);
            }

            public Size getSizeForItem(TLRPC.Document document, List<TLRPC.DocumentAttribute> attributes) {
                size.width = size.height = 100;
                size.full = false;
                if (document != null) {
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    if (thumb != null && thumb.w != 0 && thumb.h != 0) {
                        size.width = thumb.w;
                        size.height = thumb.h;
                    }
                }
                if (attributes != null) {
                    for (int b = 0; b < attributes.size(); b++) {
                        TLRPC.DocumentAttribute attribute = attributes.get(b);
                        if (attribute instanceof TLRPC.TL_documentAttributeImageSize || attribute instanceof TLRPC.TL_documentAttributeVideo) {
                            size.width = attribute.w;
                            size.height = attribute.h;
                            break;
                        }
                    }
                }
                return size;
            }
        }
    }

    private class Page extends IPage {
        public EmojiListView listView;
        public Adapter adapter;
        public GridLayoutManager layoutManager;
        public EmojiTabsStrip tabsStrip;
        public SearchField searchField;

        public int spanCount = 8;

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
                    if (adapter.getItemViewType(position) != VIEW_TYPE_EMOJI) {
                        return spanCount;
                    }
                    return 1;
                }
            });
            listView.setOnItemClickListener((view, position) -> {
                if (position < 0) {
                    return;
                }
                if (layoutManager.getItemViewType(view) == VIEW_TYPE_WIDGETS) {
                    return;
                }
                TLRPC.Document document = position >= adapter.documents.size() ? null : adapter.documents.get(position);
                if (document == plus) {
                    if (onPlusSelected != null) {
                        onPlusSelected.run();
                    }
                    dismiss();
                    return;
                }
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
                    onDocumentSelected.run(document != null ? adapter.setByDocumentId.get(document.id) : null, document, false);
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

            tabsStrip = new EmojiTabsStrip(context, resourcesProvider, false, false, false, true, 0, null) {
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

        @Override
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

        @Override
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
            listView.setPadding(dp(5), dp(16 + 36 + 50), dp(5), AndroidUtilities.navigationBarHeight + dp(onlyStickers ? 0 : 40));
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private boolean resetOnce = false;

        @Override
        public void bind(int type) {
            this.currentType = type;
            listView.emoji = type == PAGE_TYPE_EMOJI;
            layoutManager.setSpanCount(spanCount = type == PAGE_TYPE_EMOJI ? 8 : 5);
            if (!resetOnce) {
                adapter.updateItems(null);
            }
            if (categoryIndex >= 0) {
                searchField.ignoreTextChange = true;
                searchField.editText.setText("");
                searchField.ignoreTextChange = false;
                if (searchField.categoriesListView != null) {
                    searchField.categoriesListView.selectCategory(categoryIndex);
                    searchField.categoriesListView.scrollToSelected();
                    StickerCategoriesListView.EmojiCategory category = searchField.categoriesListView.getSelectedCategory();
                    if (category != null) {
                        adapter.query = searchField.categoriesListView.getSelectedCategory().emojis;
                        AndroidUtilities.cancelRunOnUIThread(adapter.searchRunnable);
                        AndroidUtilities.runOnUIThread(adapter.searchRunnable);
                    }
                }
            } else if (!TextUtils.isEmpty(query)) {
                searchField.editText.setText(query);
                if (searchField.categoriesListView != null) {
                    searchField.categoriesListView.selectCategory(null);
                    searchField.categoriesListView.scrollToStart();
                }
                AndroidUtilities.cancelRunOnUIThread(adapter.searchRunnable);
                AndroidUtilities.runOnUIThread(adapter.searchRunnable);
            } else {
                searchField.clear();
            }
            searchField.checkCategoriesView(type, greeting);

            MediaDataController.getInstance(currentAccount).checkStickers(type == PAGE_TYPE_EMOJI ? MediaDataController.TYPE_EMOJIPACKS : MediaDataController.TYPE_IMAGE);
        }

        public boolean atTop() {
            return !listView.canScrollVertically(-1);
        }

        private static final int VIEW_TYPE_PAD = 0;
        private static final int VIEW_TYPE_HEADER = 1;
        private static final int VIEW_TYPE_EMOJI = 2;
        private static final int VIEW_TYPE_NOT_FOUND = 3;
        private static final int VIEW_TYPE_WIDGETS = 4;

        private class Adapter extends RecyclerView.Adapter {

            private int lastAllSetsCount;
            private final HashMap<String, ArrayList<Long>> allEmojis = new HashMap<>();
            private final HashMap<Long, ArrayList<TLRPC.TL_stickerPack>> packsBySet = new HashMap<>();
            private final HashMap<Long, Object> setByDocumentId = new HashMap<>();

            private final ArrayList<TLRPC.TL_messages_stickerSet> allStickerSets = new ArrayList<>();
            private final ArrayList<TLRPC.TL_messages_stickerSet> stickerSets = new ArrayList<>();
            private final ArrayList<EmojiView.EmojiPack> packs = new ArrayList<>();
            private final ArrayList<TLRPC.Document> documents = new ArrayList<>();
            private final ArrayList<Long> documentIds = new ArrayList<>();
            private boolean includeNotFound;
            private int itemsCount = 0;
            private final SparseIntArray positionToSection = new SparseIntArray();

            private final TLRPC.TL_inputStickerSetShortName staticEmojiInput;

            public Adapter() {
                staticEmojiInput = new TLRPC.TL_inputStickerSetShortName();
                staticEmojiInput.short_name = "StaticEmoji";
            }

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
                setByDocumentId.clear();
                itemsCount++; // pan
                documents.add(null);
                packs.clear();
                int i = 0;
                if (currentType == PAGE_TYPE_STICKERS) {
                    if (hasWidgets()) {
                        documents.add(widgets);
                        itemsCount++;
                    }

                    ArrayList<TLRPC.Document> favorites = mediaDataController.getRecentStickers(MediaDataController.TYPE_FAVE);
                    if (favorites != null && !favorites.isEmpty()) {
                        if (faveSet == null) {
                            faveSet = new TLRPC.TL_messages_stickerSet();
                        }
                        faveSet.documents = favorites;
                        faveSet.set = new TLRPC.TL_stickerSet();
                        faveSet.set.title = LocaleController.getString(R.string.FavoriteStickers);
                        stickerSets.add(faveSet);
                    }

                    ArrayList<TLRPC.Document> recent = mediaDataController.getRecentStickers(MediaDataController.TYPE_IMAGE);
                    if (recent != null && !recent.isEmpty()) {
                        if (recentSet == null) {
                            recentSet = new TLRPC.TL_messages_stickerSet();
                        }
                        recentSet.documents = recent;
                        if (onPlusSelected != null) {
                            recentSet.documents.add(0, plus);
                        }
                        recentSet.set = new TLRPC.TL_stickerSet();
                        recentSet.set.title = LocaleController.getString(R.string.RecentStickers);
                        stickerSets.add(recentSet);
                    }
                }
                stickerSets.addAll(mediaDataController.getStickerSets(
                        currentType == PAGE_TYPE_EMOJI ?
                                MediaDataController.TYPE_EMOJIPACKS :
                                MediaDataController.TYPE_IMAGE
                ));
                for (; i < stickerSets.size(); ++i) {
                    TLRPC.TL_messages_stickerSet set = stickerSets.get(i);

                    // header
                    positionToSection.put(itemsCount, i);
                    documents.add(null);
                    itemsCount++;

                    // emoji/stickers
                    documents.addAll(set.documents);
                    itemsCount += set.documents.size();
                    Object parentObject = set;
                    if (set == recentSet) parentObject = "recent";
                    else if (set == faveSet) parentObject = "fav";
                    for (int j = 0; j < set.documents.size(); ++j) {
                        setByDocumentId.put(set.documents.get(j).id, parentObject);
                    }

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
                            for (int k = 0; k < set.documents.size(); ++k) {
                                setByDocumentId.put(set.documents.get(k).id, set);
                            }

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
                        TLRPC.TL_messages_stickerSet set = mediaDataController.getStickerSet(staticEmojiInput, false);
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
                final MediaDataController mediaDataController = MediaDataController.getInstance(currentAccount);
                final String thisQuery = query;
                if ("premium".equalsIgnoreCase(thisQuery)) {
                    ArrayList<TLRPC.Document> premiumStickers = mediaDataController.getRecentStickers(MediaDataController.TYPE_PREMIUM_STICKERS);
                    itemsCount = 0;
                    documents.clear();
                    documentIds.clear();
                    positionToSection.clear();
                    stickerSets.clear();
                    itemsCount++; // pan
                    documents.add(null);
                    documentIds.add(0L);
                    documents.addAll(premiumStickers);
                    itemsCount += premiumStickers.size();
                    activeQuery = query;
                    notifyDataSetChanged();

                    listView.scrollToPosition(0, 0);
                    searchField.showProgress(false);
                    tabsStrip.showSelected(false);
                    return;
                }
                if (currentType == PAGE_TYPE_STICKERS && Emoji.fullyConsistsOfEmojis(query)) {
                    final TLRPC.TL_messages_getStickers req = new TLRPC.TL_messages_getStickers();
                    req.emoticon = query;
                    req.hash = 0;
                    int reqId = ConnectionsManager.getInstance(currentAccount).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
                        if (!TextUtils.equals(thisQuery, query)) {
                            return;
                        }
                        itemsCount = 0;
                        documents.clear();
                        documentIds.clear();
                        positionToSection.clear();
                        stickerSets.clear();
                        itemsCount++; // pan
                        documents.add(null);
                        documentIds.add(0L);
                        if (response instanceof TLRPC.TL_messages_stickers) {
                            TLRPC.TL_messages_stickers res = (TLRPC.TL_messages_stickers) response;
                            documents.addAll(res.stickers);
                            itemsCount += res.stickers.size();
                        }
                        activeQuery = query;
                        notifyDataSetChanged();

                        listView.scrollToPosition(0, 0);
                        searchField.showProgress(false);
                        tabsStrip.showSelected(false);
                    }));
                    return;
                }
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
                            if (r.emoji == null || r.emoji.startsWith("animated_")) {
                                continue;
                            }
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
                        final ArrayList<TLRPC.StickerSetCovered> featuredStickers = mediaDataController.getFeaturedStickerSets();
                        for (int i = 0; i < result.size(); ++i) {
                            MediaDataController.KeywordResult r = result.get(i);
                            if (r.emoji == null || r.emoji.startsWith("animated_")) {
                                continue;
                            }
                            for (int j = 0; j < featuredStickers.size(); ++j) {
                                TLRPC.StickerSetCovered set = featuredStickers.get(j);
                                ArrayList<TLRPC.Document> documents = null;
                                if (set instanceof TLRPC.TL_stickerSetFullCovered) {
                                    documents = ((TLRPC.TL_stickerSetFullCovered) set).documents;
                                } else if (!set.covers.isEmpty()) {
                                    documents = set.covers;
                                } else if (set.cover != null) {
                                    documents = new ArrayList<>();
                                    documents.add(set.cover);
                                } else {
                                    continue;
                                }
                                for (int d = 0; d < documents.size(); ++d) {
                                    String emoji = MessageObject.findAnimatedEmojiEmoticon(documents.get(d), null);
                                    if (emoji != null && emoji.contains(r.emoji)) {
                                        this.documents.add(documents.get(d));
                                        itemsCount++;
                                    }
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
                }, null, false, false, false, true, 50, false);
            };

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
                } else if (viewType == VIEW_TYPE_WIDGETS) {
                    StoryWidgetsCell cell = new StoryWidgetsCell(getContext());
                    cell.setOnButtonClickListener(EmojiBottomSheet.this::onWidgetClick);
                    view = cell;
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
                    EmojiListView.EmojiImageView imageView = (EmojiListView.EmojiImageView) holder.itemView;
                    if (document == plus) {
                        imageView.setSticker(null);
                        Drawable circle = Theme.createRoundRectDrawable(dp(28), Theme.multAlpha(getThemedColor(Theme.key_chat_emojiPanelIcon), .12f));
                        Drawable drawable = getResources().getDrawable(R.drawable.filled_add_sticker).mutate();
                        drawable.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_emojiPanelIcon), PorterDuff.Mode.MULTIPLY));
                        CombinedDrawable combinedDrawable = new CombinedDrawable(circle, drawable);
                        combinedDrawable.setCustomSize(dp(56), dp(56));
                        combinedDrawable.setIconSize(dp(24), dp(24));
                        combinedDrawable.setCenter(true);
                        imageView.setDrawable(combinedDrawable);
                        return;
                    }
                    final long documentId = position >= documentIds.size() ? 0L : documentIds.get(position);
                    if (document == null && documentId == 0) {
                        return;
                    }
                    if (currentType == PAGE_TYPE_EMOJI) {
                        if (document != null) {
                            imageView.setSticker(null);
                            imageView.setEmoji(document, currentType == PAGE_TYPE_STICKERS);
                        } else {
                            imageView.setSticker(null);
                            imageView.setEmojiId(documentId, currentType == PAGE_TYPE_STICKERS);
                        }
                    } else {
                        imageView.setEmoji(null, currentType == PAGE_TYPE_STICKERS);
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
                    if (position >= 0 && position < documents.size() && documents.get(position) == widgets) {
                         return VIEW_TYPE_WIDGETS;
                    }
                    return VIEW_TYPE_EMOJI;
                }
            }

            @Override
            public int getItemCount() {
                return itemsCount;
            }
        }
    }

    public void showPremiumBulletin(String text) {
        try {
            container.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
        } catch (Exception ignored) {}
        BulletinFactory.of(container, resourcesProvider).createSimpleBulletin(
                R.raw.star_premium_2,
                LocaleController.getString(R.string.IncreaseLimit),
                premiumText(text)
        ).show(true);
    }

    private CharSequence premiumText(String text) {
        return AndroidUtilities.replaceSingleTag(text, Theme.key_chat_messageLinkIn, 0, this::openPremium, resourcesProvider);
    }

    private void openPremium() {
        Bulletin.hideVisible();
        PremiumFeatureBottomSheet sheet = new PremiumFeatureBottomSheet(new BaseFragment() {
            { currentAccount = EmojiBottomSheet.this.currentAccount; }
            @Override
            public Dialog showDialog(Dialog dialog) {
                dialog.show();
                return dialog;
            }
            @Override
            public Activity getParentActivity() {
                return LaunchActivity.instance;
            }

            @Override
            public Theme.ResourcesProvider getResourceProvider() {
                return new WrappedResourceProvider(resourcesProvider) {
                    @Override
                    public void appendColors() {
                        sparseIntArray.append(Theme.key_dialogBackground, 0xFF1E1E1E);
                        sparseIntArray.append(Theme.key_windowBackgroundGray, 0xFF000000);
                    }
                };
            }

            @Override
            public boolean isLightStatusBar() {
                return false;
            }
        }, PremiumPreviewFragment.PREMIUM_FEATURE_STORIES, false);
        sheet.setOnDismissListener(d -> {

        });
        sheet.show();
    }

    public boolean canShowWidget(Integer id) {
        return true;
    }

    public boolean canClickWidget(Integer id) {
        return true;
    }

    public boolean hasWidgets() {
        return onWidgetSelected != null && (canShowWidget(WIDGET_LOCATION) || canShowWidget(WIDGET_AUDIO) || canShowWidget(WIDGET_PHOTO) || canShowWidget(WIDGET_REACTION) || canShowWidget(WIDGET_LINK));
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

    private void onWidgetClick(int id) {
        if (canClickWidget(id)) {
            if (id == WIDGET_AUDIO) {
                if (!checkAudioPermission(() -> onWidgetClick(id))) {
                    return;
                }
            }
            if (onWidgetSelected.run(id)) {
                dismiss();
            }
        }
    }

    protected boolean checkAudioPermission(Runnable granted) {
        return true;
    }

    private final ViewPagerFixed viewPager;
    private TabsView tabsView;
    private float maxPadding = -1;
    private final boolean onlyStickers;
    private final boolean greeting;

//    private final GestureDetector gestureDetector;
    private boolean wasKeyboardVisible;

    public static int savedPosition = 1;

    public EmojiBottomSheet(Context context, boolean onlyStickers, Theme.ResourcesProvider resourcesProvider, boolean greeting) {
        super(context, true, resourcesProvider);

        this.onlyStickers = onlyStickers;
        this.greeting = greeting;

        useSmoothKeyboard = true;
        fixNavigationBar(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));

        occupyNavigationBar = true;
        setUseLightStatusBar(false);

        containerView = new ContainerView(context);
        viewPager = new ViewPagerFixed(context) {
            @Override
            protected void onTabAnimationUpdate(boolean manual) {
                if (tabsView != null) {
                    tabsView.setType(viewPager.getPositionAnimated());
                }
                containerView.invalidate();
                invalidate();
                savedPosition = viewPager.getCurrentPosition();
            }
        };
        viewPager.currentPosition = onlyStickers ? 0 : savedPosition;
        viewPager.setAdapter(new ViewPagerFixed.Adapter() {
            @Override
            public int getItemCount() {
                return onlyStickers ? 1 : 3;
            }
            @Override
            public View createView(int viewType) {
                if (viewType == 1) {
                    return new GifPage(context);
                }
                return new Page(context);
            }

            @Override
            public int getItemViewType(int position) {
                if (position == 0 || position == 1) {
                    return 0;
                } else {
                    return 1;
                }
            }

            @Override
            public void bindView(View view, int position, int viewType) {
                ((IPage) view).bind(onlyStickers ? 1 : position);
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

        if (!onlyStickers) {
            tabsView = new TabsView(context);
            tabsView.setOnTypeSelected(type -> {
                if (!viewPager.isManualScrolling() && viewPager.getCurrentPosition() != type) {
                    viewPager.scrollToPosition(type);
                    tabsView.setType(type);
                }
            });
            tabsView.setType(viewPager.currentPosition);
            containerView.addView(tabsView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.BOTTOM | Gravity.FILL_HORIZONTAL));
        }

        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).addObserver(this, NotificationCenter.groupStickersDidLoad);
        FileLog.disableGson(true);

        if (!onlyStickers) {
            MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_EMOJIPACKS);
            MediaDataController.getInstance(currentAccount).checkFeaturedEmoji();
            MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, true, true, false);
        }

        MediaDataController.getInstance(currentAccount).checkStickers(MediaDataController.TYPE_IMAGE);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_IMAGE, false, true, false);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_FAVE, false, true, false);
        MediaDataController.getInstance(currentAccount).loadRecents(MediaDataController.TYPE_PREMIUM_STICKERS, false, true, false);
    }

    public void closeKeyboard() {
        keyboardVisible = false;
        container.animate().translationY(0).setDuration(AdjustPanLayoutHelper.keyboardDuration).setInterpolator(AdjustPanLayoutHelper.keyboardInterpolator).start();
        View[] views = viewPager.getViewPages();
        for (int i = 0; i < views.length; ++i) {
            View view = views[i];
            if (view instanceof Page) {
                Page page = (Page) view;
                if (page.searchField != null) {
                    AndroidUtilities.hideKeyboard(page.searchField.editText);
                }
            } else if (view instanceof GifPage) {
                GifPage page = (GifPage) view;
                if (page.searchField != null) {
                    AndroidUtilities.hideKeyboard(page.searchField.editText);
                }
            }
        }
    }

    @Override
    public void dismiss() {
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.stickersDidLoad);
        NotificationCenter.getInstance(currentAccount).removeObserver(this, NotificationCenter.groupStickersDidLoad);
        closeKeyboard();
        super.dismiss();
        FileLog.disableGson(false);
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
            if (blurBitmap != null || !(resourcesProvider == null ? Theme.isCurrentThemeDark() : resourcesProvider.isDark()) || drawBlurBitmap == null || SharedConfig.getDevicePerformanceClass() <= SharedConfig.PERFORMANCE_CLASS_LOW || LiteMode.isPowerSaverApplied()) {
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
            blurBitmapMatrix.postScale(scale, scale);
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
            if (tabsView != null) {
                tabsView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), 0);
            }
            setMeasuredDimension(width, height);
        }

        private Boolean overStatusBar;

        @Override
        protected void dispatchDraw(Canvas canvas) {
            backgroundPaint.setColor(Theme.getColor(Theme.key_dialogBackground, resourcesProvider));
            backgroundPaint.setAlpha((int) (0xFF * (blurBitmap == null ? 1f : .85f)));
            View[] views = viewPager.getViewPages();
            top = 0;
            for (int i = 0; i < views.length; ++i) {
                View view = views[i];
                if (!(view instanceof IPage)) {
                    continue;
                }
                IPage page = (IPage) view;
                top += page.top() * Utilities.clamp(1f - Math.abs(page.getTranslationX() / (float) page.getMeasuredWidth()), 1, 0);
                if (page.getVisibility() == View.VISIBLE) {
                    page.updateTops();
                }
            }
            final float statusBar = isActionBarT.set(top <= 0 ? 1 : 0);
            final float y = top + viewPager.getPaddingTop() - lerp(dp(8), viewPager.getPaddingTop(), statusBar);
            AndroidUtilities.rectTmp.set(backgroundPaddingLeft, y, getWidth() - backgroundPaddingLeft, getHeight() + AndroidUtilities.dp(8));
            if (blurBitmap != null) {
                blurBitmapMatrix.reset();
                blurBitmapMatrix.postScale(16, 16);
                blurBitmapMatrix.postTranslate(0, -getY());
                blurBitmapShader.setLocalMatrix(blurBitmapMatrix);
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(14), dp(14), backgroundBlurPaint);
            }

            boolean overStatusBar = AndroidUtilities.rectTmp.top < AndroidUtilities.statusBarHeight;
            if (this.overStatusBar == null || this.overStatusBar != overStatusBar) {
                this.overStatusBar = overStatusBar;
                AndroidUtilities.setLightStatusBar(getWindow(), overStatusBar ? AndroidUtilities.computePerceivedBrightness(backgroundPaint.getColor()) >= .721f : false);
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
        public boolean dispatchTouchEvent(MotionEvent event) {
            if (event.getAction() == MotionEvent.ACTION_DOWN && event.getY() < top) {
                dismiss();
                return true;
            }
            return super.dispatchTouchEvent(event);
        }
    }

    @Override
    public int getContainerViewHeight() {
        if (containerView.getMeasuredHeight() <= 0) {
            return AndroidUtilities.displaySize.y;
        }
        return (int) (containerView.getMeasuredHeight() - viewPager.getY());
    }

    private Utilities.Callback3Return<Object, TLRPC.Document, Boolean, Boolean> onDocumentSelected;
    private Runnable onPlusSelected;
    public EmojiBottomSheet whenDocumentSelected(Utilities.Callback3Return<Object, TLRPC.Document, Boolean, Boolean> listener) {
        this.onDocumentSelected = listener;
        return this;
    }
    private Utilities.CallbackReturn<Integer, Boolean> onWidgetSelected;

    public EmojiBottomSheet whenPlusSelected(Runnable listener) {
        this.onPlusSelected = listener;
        View[] pages = viewPager.getViewPages();
        for (int i = 0; i < pages.length; ++i) {
            View view = pages[i];
            if (view instanceof Page) {
                Page page = (Page) view;
                page.adapter.update();
            }
        }
        return this;
    }
    public EmojiBottomSheet whenWidgetSelected(Utilities.CallbackReturn<Integer, Boolean> listener) {
        this.onWidgetSelected = listener;
        View[] pages = viewPager.getViewPages();
        for (int i = 0; i < pages.length; ++i) {
            View view = pages[i];
            if (view instanceof Page) {
                Page page = (Page) view;
                page.adapter.update();
            }
        }
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
            public AnimatedEmojiDrawable drawable;
            private final EmojiListView listView;
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

            public void setDrawable(Drawable drawable) {
                if (this.drawable != null) {
                    this.drawable.removeView(this);
                }
                this.drawable = null;
                documentId = 0;
                emoji = false;
                if (imageReceiver == null) {
                    imageReceiver = new ImageReceiver();
                    imageReceiver.setLayerNum(7);
                    imageReceiver.setAspectFit(true);
                    if (attached) {
                        imageReceiver.onAttachedToWindow();
                    }
                }
                imageReceiver.setImageBitmap(drawable);
            }

            public void setEmoji(TLRPC.Document document, boolean isSticker) {
                if (documentId == (document == null ? 0 : document.id)) {
                    return;
                }
                if (drawable != null) {
                    drawable.removeView(this);
                }
                if (document != null) {
                    emoji = true;
                    documentId = document.id;
                    drawable = AnimatedEmojiDrawable.make(currentAccount, getCacheType(isSticker), document);
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

            public void setEmojiId(long documentId, boolean isSticker) {
                if (this.documentId == documentId) {
                    return;
                }
                if (drawable != null) {
                    drawable.removeView(this);
                }
                if (documentId != 0) {
                    emoji = true;
                    this.documentId = documentId;
                    drawable = AnimatedEmojiDrawable.make(currentAccount, getCacheType(isSticker), documentId);
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
                        if (attached) {
                            imageReceiver.onAttachedToWindow();
                        }
                    }
                    imageReceiver.setParentView(!emoji ? this : listView);

                    SvgHelper.SvgDrawable svgThumb = null;//DocumentObject.getSvgThumb(document, Theme.key_windowBackgroundWhiteGrayIcon, 0.2f);
                    TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 90);
                    String filter = "80_80";
                    if ("video/webm".equals(document.mime_type)) {
                        filter += "_" + ImageLoader.AUTOPLAY_FILTER;
                    }
                    if (!LiteMode.isEnabled(LiteMode.FLAG_ANIMATED_STICKERS_KEYBOARD)) {
                        filter += "_firstframe";
                    }
//                    if (svgThumb != null) {
//                        svgThumb.overrideWidthAndHeight(512, 512);
//                    }
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
                    drawable.addView(this);
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
                    drawable.removeView(this);
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
                    imageReceiver.setImageCoords(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
                    imageReceiver.draw(canvas);
                } else if (drawable != null) {
                    drawable.setBounds(getPaddingLeft(), getPaddingTop(), getWidth() - getPaddingRight(), getHeight() - getPaddingBottom());
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

            if (!emoji) {
                super.dispatchDraw(canvas);
                canvas.restore();
                return;
            }

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
                        if (aspectRatio < 1) {
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
        private int categoriesListViewType = -1;
        @Nullable
        private StickerCategoriesListView categoriesListView;
        private boolean clearVisible;
        private final ImageView clear;

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
                        editText.requestFocus();
                        AndroidUtilities.showKeyboard(editText);
                    }
                    return super.onTouchEvent(event);
                }

                @Override
                protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
                    super.onFocusChanged(focused, direction, previouslyFocusedRect);
                    if (!focused) {
                        AndroidUtilities.hideKeyboard(editText);
                    }
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
            editText.setHint(LocaleController.getString(R.string.Search));
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
                        if (clear != null && clearVisible != (!TextUtils.isEmpty(editText.getText()))) {
                            clearVisible = !clearVisible;
                            clear.animate().cancel();
                            if (clearVisible) {
                                clear.setVisibility(View.VISIBLE);
                            }
                            clear.animate()
                                .scaleX(clearVisible ? 1f : .7f)
                                .scaleY(clearVisible ? 1f : .7f)
                                .alpha(clearVisible ? 1f : 0f)
                                .withEndAction(() -> {
                                    if (!clearVisible) {
                                        clear.setVisibility(View.GONE);
                                    }
                                })
                                .setInterpolator(CubicBezierInterpolator.EASE_OUT_QUINT)
                                .setDuration(320)
                                .setStartDelay(clearVisible ? 240 : 0)
                                .start();
                        }
                    }
                }
            });

            clear = new ImageView(context);
            clear.setScaleType(ImageView.ScaleType.CENTER);
            clear.setImageDrawable(new CloseProgressDrawable2(1.25f) {
                { setSide(AndroidUtilities.dp(7)); }
                @Override
                protected int getCurrentColor() {
                    return Theme.getColor(Theme.key_chat_emojiSearchIcon, resourcesProvider);
                }
            });
            clear.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), Theme.RIPPLE_MASK_CIRCLE_20DP, AndroidUtilities.dp(15)));
            clear.setAlpha(0f);
            clear.setScaleX(.7f);
            clear.setScaleY(.7f);
            clear.setVisibility(View.GONE);
            clear.setOnClickListener(e -> clear());
            box.addView(clear, LayoutHelper.createFrame(36, 36, Gravity.RIGHT | Gravity.TOP));


            searchImageView.setOnClickListener(e -> {
                if (searchImageDrawable.getIconState() == SearchStateDrawable.State.STATE_BACK) {
                    clear();
                    if (categoriesListView != null) {
                        categoriesListView.scrollToStart();
                    }
                } else if (searchImageDrawable.getIconState() == SearchStateDrawable.State.STATE_SEARCH) {
                    editText.requestFocus();
                }
            });
        }

        public void checkCategoriesView(int type, boolean greeting) {
            if (categoriesListViewType == type && categoriesListView != null) return;
            if (categoriesListView != null) {
                box.removeView(categoriesListView);
            }
            categoriesListView = new StickerCategoriesListView(getContext(), null, type == PAGE_TYPE_STICKERS ? StickerCategoriesListView.CategoriesType.STICKERS : StickerCategoriesListView.CategoriesType.DEFAULT, resourcesProvider) {
                @Override
                public void selectCategory(int categoryIndex) {
                    super.selectCategory(categoryIndex);
                    updateButton();
                }

                @Override
                protected EmojiCategory[] preprocessCategories(EmojiCategory[] categories) {
                    if (categories != null && greeting) {
                        int index = -1;
                        for (int i = 0; i < categories.length; ++i) {
                            if (categories[i] != null && categories[i].greeting) {
                                index = i;
                                break;
                            }
                        }
                        if (index >= 0) {
                            EmojiCategory[] newCategories = new EmojiCategory[categories.length];
                            newCategories[0] = categories[index];
                            for (int i = 1; i < newCategories.length; ++i) {
                                newCategories[i] = categories[i <= index ? i - 1 : i];
                            }
                            return newCategories;
                        }
                    }
                    return categories;
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
                updateButton();
            });
            categoriesListView.setOnCategoryClick(category -> {
                if (categoriesListView == null) {
                    return;
                }
                if (categoriesListView.getSelectedCategory() == category) {
                    categoriesListView.selectCategory(null);
                    search(null, -1);
                } else {
                    categoriesListView.selectCategory(category);
                    search(category.emojis, categoriesListView.getCategoryIndex());
                }
            });
            box.addView(categoriesListView, Math.max(0, box.getChildCount() - 1), LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 36, Gravity.LEFT | Gravity.TOP, 36, 0, 0, 0));
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
                boolean backButton = editText.length() > 0 || categoriesListView != null && categoriesListView.isCategoriesShown() && (categoriesListView != null && categoriesListView.isScrolledIntoOccupiedWidth() || categoriesListView.getSelectedCategory() != null);
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
            if (categoriesListView != null) {
                categoriesListView.selectCategory(null);
            }
        }
    }

    private static class TabsView extends View {

        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        private final Paint selectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private StaticLayout emojiLayout;
        private float emojiLayoutWidth, emojiLayoutLeft;
        private StaticLayout stickersLayout;
        private float stickersLayoutWidth, stickersLayoutLeft;
        private StaticLayout gifsLayout;
        private float gifsLayoutWidth, gifsLayoutLeft;

        private final RectF emojiRect = new RectF();
        private final RectF stickersRect = new RectF();
        private final RectF gifsRect = new RectF();
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
            textPaint.setTypeface(AndroidUtilities.bold());

            emojiLayout = new StaticLayout(LocaleController.getString("Emoji"), textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            emojiLayoutWidth = emojiLayout.getLineCount() >= 1 ? emojiLayout.getLineWidth(0) : 0;
            emojiLayoutLeft = emojiLayout.getLineCount() >= 1 ? emojiLayout.getLineLeft(0) : 0;

            stickersLayout = new StaticLayout(LocaleController.getString("AccDescrStickers"), textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            stickersLayoutWidth = stickersLayout.getLineCount() >= 1 ? stickersLayout.getLineWidth(0) : 0;
            stickersLayoutLeft = stickersLayout.getLineCount() >= 1 ? stickersLayout.getLineLeft(0) : 0;

            gifsLayout = new StaticLayout(LocaleController.getString(R.string.AccDescrGIFs), textPaint, getMeasuredWidth(), Layout.Alignment.ALIGN_NORMAL, 1, 0, false);
            gifsLayoutWidth = gifsLayout.getLineCount() >= 1 ? gifsLayout.getLineWidth(0) : 0;
            gifsLayoutLeft = gifsLayout.getLineCount() >= 1 ? gifsLayout.getLineLeft(0) : 0;

            float w = dp(12) + emojiLayoutWidth + dp(12 + 12 + 12) + stickersLayoutWidth + dp(12 + 12 + 12) + gifsLayoutWidth + dp(12);
            float t = dp(40 - 26) / 2f, b = dp(40 + 26) / 2f;
            float l = (getMeasuredWidth() - w) / 2f;

            emojiRect.set(l, t, l + emojiLayoutWidth + dp(12 + 12), b);
            l += emojiLayoutWidth + dp(12 + 12 + 12);

            stickersRect.set(l, t, l + stickersLayoutWidth + dp(12 + 12), b);
            l += stickersLayoutWidth + dp(12 + 12 + 12);

            gifsRect.set(l, t, l + gifsLayoutWidth + dp(12 + 12), b);
            l += gifsLayoutWidth + dp(12 + 12 + 12);
        }

        private RectF getRect(int t) {
            if (t <= 0) {
                return emojiRect;
            } else if (t == 1) {
                return stickersRect;
            } else {
                return gifsRect;
            }
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

            if (gifsLayout != null) {
                canvas.save();
                canvas.translate(gifsRect.left + dp(12) - gifsLayoutLeft, gifsRect.top + (gifsRect.height() - gifsLayout.getHeight()) / 2f);
                textPaint.setColor(ColorUtils.blendARGB(0xFF838383, 0xFFFFFFFF, Utilities.clamp(1f - Math.abs(type - 2), 1, 0)));
                gifsLayout.draw(canvas);
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
                } else if (gifsRect.contains(event.getX(), event.getY())) {
                    onTypeSelected.run(2);
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
            textView.setText(emoji ? LocaleController.getString(R.string.NoEmojiFound) : LocaleController.getString(R.string.NoStickersFound));
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

    public static final int WIDGET_LOCATION = 0;
    public static final int WIDGET_AUDIO = 1;
    public static final int WIDGET_PHOTO = 2;
    public static final int WIDGET_REACTION = 3;
    public static final int WIDGET_LINK = 4;
    public static final int WIDGET_WEATHER = 5;

    private class StoryWidgetsCell extends View {

        private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final TextPaint textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        {
            bgPaint.setColor(0x19ffffff);
            textPaint.setTypeface(AndroidUtilities.getTypeface("fonts/rcondensedbold.ttf"));
            textPaint.setTextSize(dpf2(21.3f));
            textPaint.setColor(Color.WHITE);
        }

        private final List<BaseWidget> widgets = new ArrayList<>();

        public StoryWidgetsCell(Context context) {
            super(context);
            setPadding(0, 0, 0, 0);
            if (canShowWidget(WIDGET_LINK))
                widgets.add(new Button(WIDGET_LINK, R.drawable.msg_limit_links, LocaleController.getString(R.string.StoryWidgetLink)).needsPremium());
            if (canShowWidget(WIDGET_LOCATION))
                widgets.add(new Button(WIDGET_LOCATION, R.drawable.map_pin3, LocaleController.getString(R.string.StoryWidgetLocation)));
            if (canShowWidget(WIDGET_WEATHER)) {
                Weather.State weather = Weather.getCached();
                Button[] btn = new Button[] { null };
                CharSequence text = Emoji.replaceEmoji((weather == null ? "🌤" : weather.getEmoji()) + " " + (weather == null ? (Weather.isDefaultCelsius() ? "24°C" : "72°F") : weather.getTemperature()), textPaint.getFontMetricsInt(), false);
                if (MessagesController.getInstance(currentAccount).storyWeatherPreload && PermissionRequest.hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION) && weather == null) {
                    text = new SpannableStringBuilder("___");
                    ((SpannableStringBuilder) text).setSpan(new LoadingSpan(this, dp(68)), 0, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    btn[0] = new Button(this, WIDGET_WEATHER, text);
                    Weather.fetch(false, state -> {
                        btn[0].setText(Emoji.replaceEmoji((state == null ? "🌤" : state.getEmoji()) + " " + (state == null ? (Weather.isDefaultCelsius() ? "24°C" : "72°F") : state.getTemperature()), textPaint.getFontMetricsInt(), false));
                        invalidate();
                        requestLayout();
                    });
                }
                widgets.add(btn[0] == null ? new Button(this, WIDGET_WEATHER, text) : btn[0]);
            }
            if (canShowWidget(WIDGET_AUDIO))
                widgets.add(new Button(WIDGET_AUDIO, R.drawable.filled_widget_music, LocaleController.getString(R.string.StoryWidgetAudio)));
            if (canShowWidget(WIDGET_PHOTO))
                widgets.add(new Button(WIDGET_PHOTO, R.drawable.filled_premium_camera, LocaleController.getString(R.string.StoryWidgetPhoto)));
            if (canShowWidget(WIDGET_REACTION))
                widgets.add(new ReactionWidget());
        }

        private abstract class BaseWidget {
            int id;
            float width, height;
            float layoutX = 0;
            int layoutLine = 0;
            RectF bounds = new RectF();
            ButtonBounce bounce = new ButtonBounce(StoryWidgetsCell.this);
            public AnimatedFloat animatedWidth = new AnimatedFloat(StoryWidgetsCell.this, 350, CubicBezierInterpolator.EASE_OUT_QUINT);

            abstract void draw(Canvas canvas, float left, float top);

            public void onAttachToWindow(boolean attached) {

            }

        }

        private class Button extends BaseWidget {

            String emojiDrawable;
            Drawable drawable;
            Drawable lockDrawable;
            StaticLayout layout;
            float textWidth;
            float textLeft;
            Paint lockPaint;


            public Button(int id, int iconId, String string) {
                this.id = id;
                this.drawable = getContext().getResources().getDrawable(iconId).mutate();
                this.drawable.setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN));
                CharSequence text = string.toUpperCase();
                text = TextUtils.ellipsize(text, textPaint, AndroidUtilities.displaySize.x * .8f, TextUtils.TruncateAt.END);
                this.layout = new StaticLayout(text, textPaint, 99999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                this.textWidth = this.layout.getLineCount() > 0 ? this.layout.getLineWidth(0) : 0;
                this.textLeft = this.layout.getLineCount() > 0 ? this.layout.getLineLeft(0) : 0;
                this.width = dpf2(6 + 24 + 4 + 11.6f) + this.textWidth;
                this.height = dpf2(36);
            }

            public Button(View view, int id, CharSequence text) {
                this.id = id;
                text = TextUtils.ellipsize(text, textPaint, AndroidUtilities.displaySize.x * .8f, TextUtils.TruncateAt.END);
                this.layout = new StaticLayout(text, textPaint, 99999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                this.textWidth = this.layout.getLineCount() > 0 ? this.layout.getLineWidth(0) : 0;
                this.textLeft = this.layout.getLineCount() > 0 ? this.layout.getLineLeft(0) : 0;
                this.width = dpf2(6 + 6) + this.textWidth;
                this.height = dpf2(36);
            }

            public void setText(CharSequence text) {
                text = TextUtils.ellipsize(text, textPaint, AndroidUtilities.displaySize.x * .8f, TextUtils.TruncateAt.END);
                this.layout = new StaticLayout(text, textPaint, 99999, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
                this.textWidth = this.layout.getLineCount() > 0 ? this.layout.getLineWidth(0) : 0;
                this.textLeft = this.layout.getLineCount() > 0 ? this.layout.getLineLeft(0) : 0;
                this.width = dpf2(6 + 11.6f) + this.textWidth;
                this.height = dpf2(36);
            }

            public Button needsPremium() {
                if (!UserConfig.getInstance(currentAccount).isPremium()) {
                    lockDrawable = getContext().getResources().getDrawable(R.drawable.msg_mini_lock3).mutate();
                    lockDrawable.setColorFilter(new PorterDuffColorFilter(Theme.multAlpha(Color.WHITE, .60f), PorterDuff.Mode.SRC_IN));
                    lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
                    lockPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));
                }
                return this;
            }

            public void draw(Canvas canvas, float left, float top) {
                bounds.set(left, top, left + width, top + height);
                final float scale = bounce.getScale(.05f);
                canvas.save();
                canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
                canvas.drawRoundRect(bounds, dp(8), dp(8), bgPaint);
                if (lockDrawable != null) {
                    canvas.saveLayerAlpha(bounds, 0xFF, Canvas.ALL_SAVE_FLAG);
                }
                if (drawable == null) {
                    drawable = Emoji.getEmojiBigDrawable(emojiDrawable);
                    if (this.drawable instanceof Emoji.EmojiDrawable) {
                        ((Emoji.EmojiDrawable) this.drawable).fullSize = false;
                    }
                }
                if (drawable != null) {
                    int sz = dp(emojiDrawable == null ? 24 : 22);
                    drawable.setBounds(
                            (int) (bounds.left + dp(6 + 12) - sz / 2),
                            (int) (bounds.top + height / 2 - sz / 2),
                            (int) (bounds.left + dp(6 + 12) + sz / 2),
                            (int) (bounds.top + height / 2 + sz / 2)
                    );
                    drawable.draw(canvas);
                }
                if (lockDrawable != null) {
                    AndroidUtilities.rectTmp.set(
                        bounds.left + dp(6 + 24 - 12 + .55f),
                        bounds.top + height - dp(5) - dp(12 + .55f),
                        bounds.left + dp(6 + 24 - .55f),
                        bounds.left + dp(6 + 24 + 1)
                    );
                    canvas.drawRoundRect(
                        AndroidUtilities.rectTmp,
                        dp(6), dp(6),
                        lockPaint
                    );
                    lockDrawable.setBounds(
                            (int) (bounds.left + dp(6 + 24 - 12)),
                            (int) (bounds.top + height - dp(5) - dp(12)),
                            (int) (bounds.left + dp(6 + 24)),
                            (int) (bounds.top + height - dp(5))
                    );
                    lockDrawable.draw(canvas);
                    canvas.restore();
                }
                canvas.translate(bounds.left + dp(6 + (drawable == null && emojiDrawable == null ? 0 : 24 + 4)) - textLeft, bounds.top + height / 2 - layout.getHeight() / 2f);
                layout.draw(canvas);
                canvas.restore();
            }
        }

        private class ReactionWidget extends BaseWidget {

            ReactionImageHolder reactionHolder = new ReactionImageHolder(StoryWidgetsCell.this);
            ReactionImageHolder nextReactionHolder = new ReactionImageHolder(StoryWidgetsCell.this);
            int currentIndex;
            AnimatedFloat progressToNext = new AnimatedFloat(StoryWidgetsCell.this);
            Timer timeTimer;

            StoryReactionWidgetBackground background = new StoryReactionWidgetBackground(StoryWidgetsCell.this);
            ArrayList<ReactionsLayoutInBubble.VisibleReaction> visibleReactions = new ArrayList<>();
            ReactionWidget() {
                id = WIDGET_REACTION;
                width = AndroidUtilities.dp(44);
                height = AndroidUtilities.dp(36);

                List<TLRPC.TL_availableReaction> availableReactions = MediaDataController.getInstance(currentAccount).getReactionsList();
                for (int i = 0; i < Math.min(availableReactions.size(), 8); i++) {
                    visibleReactions.add(ReactionsLayoutInBubble.VisibleReaction.fromEmojicon(availableReactions.get(i)));
                }
                Collections.sort(visibleReactions, (o1, o2) -> {
                    int i1 = o1.emojicon != null && o1.emojicon.equals("❤") ? -1 : 0;
                    int i2 = o2.emojicon != null && o2.emojicon.equals("❤") ? -1 : 0;
                    return i1 - i2;
                });
                if (!visibleReactions.isEmpty()) {
                    reactionHolder.setVisibleReaction(visibleReactions.get(currentIndex));
                }

                progressToNext.set(1, true);
            }

            @Override
            void draw(Canvas canvas, float left, float top) {
                top -= AndroidUtilities.dp(4);
                bounds.set((int) left, (int) top, (int) (left + width), (int) (top + width));
                final float scale = bounce.getScale(.05f);
                canvas.save();
                canvas.scale(scale, scale, bounds.centerX(), bounds.centerY());
                background.setBounds((int) bounds.left, (int) bounds.top, (int) bounds.right, (int) bounds.bottom);
                background.draw(canvas);
                float imageSize = AndroidUtilities.dp(30);
                AndroidUtilities.rectTmp2.set(
                        (int) (bounds.centerX() - imageSize / 2f),
                        (int) (bounds.centerY() - imageSize / 2f),
                        (int) (bounds.centerX() + imageSize / 2f),
                        (int) (bounds.centerY() + imageSize / 2f)
                );
                float progress = progressToNext.set(1);
                nextReactionHolder.setBounds(AndroidUtilities.rectTmp2);
                reactionHolder.setBounds(AndroidUtilities.rectTmp2);
                if (progress == 1) {
                    reactionHolder.draw(canvas);
                } else {
                    canvas.save();
                    canvas.scale(1f - progress, 1f - progress, bounds.centerX(), bounds.top);
                    nextReactionHolder.setAlpha(1f - progress);
                    nextReactionHolder.draw(canvas);
                    canvas.restore();

                    canvas.save();
                    canvas.scale(progress, progress, bounds.centerX(), bounds.bottom);
                    reactionHolder.setAlpha(progress);
                    reactionHolder.draw(canvas);
                    canvas.restore();
                }
                canvas.restore();
            }

            @Override
            public void onAttachToWindow(boolean attached) {
                super.onAttachToWindow(attached);
                reactionHolder.onAttachedToWindow(attached);
                nextReactionHolder.onAttachedToWindow(attached);
                if (timeTimer != null) {
                    timeTimer.cancel();
                    timeTimer = null;
                }
                if (attached) {
                    timeTimer = new Timer();
                    timeTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            AndroidUtilities.runOnUIThread(() -> {
                                if (visibleReactions.isEmpty()) {
                                    return;
                                }
                                progressToNext.set(0, true);
                                currentIndex++;
                                if (currentIndex > visibleReactions.size() - 1) {
                                    currentIndex = 0;
                                }
                                ReactionImageHolder k = nextReactionHolder;
                                nextReactionHolder.setVisibleReaction(visibleReactions.get(currentIndex));
                                nextReactionHolder = reactionHolder;
                                reactionHolder = k;
                                invalidate();
                            });
                        }
                    }, 2000, 2000);
                }
            }
        }

        float[] lineWidths;

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int y = 1;
            float x = 0;

            final int width = MeasureSpec.getSize(widthMeasureSpec);
            final int availableWidth = (int) (width - getPaddingLeft() - getPaddingRight());

            for (final BaseWidget widget : widgets) {
                widget.layoutX = x;
                x += widget.width + dp(10);
                if (x > availableWidth) {
                    y++;
                    widget.layoutX = x = 0;
                    x += widget.width + dp(10);
                }
                widget.layoutLine = y;
            }

            final int linesCount = y;
            if (lineWidths == null || lineWidths.length != linesCount) {
                lineWidths = new float[linesCount];
            } else {
                Arrays.fill(lineWidths, 0);
            }
            for (final BaseWidget widget : widgets) {
                final int i = widget.layoutLine - 1;
                if (lineWidths[i] > 0)
                    lineWidths[i] += dp(10);
                lineWidths[i] += widget.width;
            }

            final int height = dp(12 + 12) + y * dp(36) + (y - 1) * dp(12);
            setMeasuredDimension(width, height);
        }

        @Override
        protected void dispatchDraw(Canvas canvas) {
            try {
                for (int i = 0; i < lineWidths.length; ++i) {
                    lineWidths[i] = 0;
                }
                for (final BaseWidget widget : widgets) {
                    final int i = widget.layoutLine - 1;
                    if (lineWidths[i] > 0)
                        lineWidths[i] += dp(10);
                    lineWidths[i] += widget.animatedWidth.set(widget.width);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
            for (final BaseWidget widget : widgets) {
                final float left = getPaddingLeft() + ((getMeasuredWidth() - getPaddingLeft() - getPaddingRight() - lineWidths[widget.layoutLine - 1]) / 2f) + widget.layoutX;
                final float top = dp(12) + (widget.layoutLine - 1) * dp(36 + 12);
                widget.draw(canvas, left, top);
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            BaseWidget touchButton = null;
            for (final BaseWidget widget : widgets) {
                if (widget.bounds.contains(event.getX(), event.getY())) {
                    touchButton = widget;
                    break;
                }
            }
            for (final BaseWidget widget : widgets) {
                if (widget != touchButton) {
                    widget.bounce.setPressed(false);
                }
            }
            if (touchButton != null) {
                touchButton.bounce.setPressed(event.getAction() != MotionEvent.ACTION_UP && event.getAction() != MotionEvent.ACTION_CANCEL);
            }
            if (event.getAction() == MotionEvent.ACTION_UP && touchButton != null) {
                if (onClickListener != null) {
                    onClickListener.run(touchButton.id);
                }
            }
            return touchButton != null;
        }

        private Utilities.Callback<Integer> onClickListener;
        public void setOnButtonClickListener(Utilities.Callback<Integer> listener) {
            onClickListener = listener;
        }

        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            for (BaseWidget widget : widgets) {
                widget.onAttachToWindow(true);
            }
        }


        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            for (BaseWidget widget : widgets) {
                widget.onAttachToWindow(false);
            }
        }
    }

    public static int getCacheType(boolean stickers) {
        return LiteMode.isEnabled(stickers ? LiteMode.FLAG_ANIMATED_STICKERS_KEYBOARD : LiteMode.FLAG_ANIMATED_EMOJI_KEYBOARD) ?
            AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW : AnimatedEmojiDrawable.CACHE_TYPE_ALERT_PREVIEW_STATIC;
    }
}
