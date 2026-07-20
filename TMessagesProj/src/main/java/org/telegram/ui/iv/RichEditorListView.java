package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.text.Editable;
import android.text.Layout;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewParent;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.Space;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.CodeHighlighting;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.AppGlobalConfig;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.messenger.Utilities;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.FloatingToolbar;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.ChatAttachAlert;
import org.telegram.ui.Components.ItemOptions;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.BreakIterator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class RichEditorListView extends UniversalRecyclerView {

    public interface Delegate {
        ItemOptions makeMenu(View anchor);
        void onSelectionChanged();
        void onContentChanged();
        void onHistoryChanged();
        void onOpenAttachRequest(int allowedLayouts, int initialLayoutType);
        void onOpenLocationRequest(BlockRow row);
        void onSlashSuggest(RichTextCell cell, String query);
        void onListScrolled(int dy);
        void onListLayoutUpdated();
        void makeEditTextFocusable(RichEditText et, boolean showKeyboard);
        void onReorderStart();
        boolean onReorderMove(float screenX, float screenY);
        void onReorderEnd();
    }

    private int currentAccount;
    private Theme.ResourcesProvider resourcesProvider;
    private Delegate delegate;

    TL_iv.RichMessage loadedRichMessage;

    final ArrayList<BlockRow> rows = new ArrayList<>();

    final HashMap<Long, TL_iv.RichText> quoteAuthors = new HashMap<>();

    TextSelectionHelper.ArticleTextSelectionHelper textSelectionHelper;
    TextSelectionHelper.TextSelectionOverlay textSelectionOverlay;

    private int restoreFocusCell = -1;
    private int restoreFocusOffset = -1;
    private int restoreFocusChildPosition = 0;
    private boolean pendingTapDismiss;
    private float pendingTapRawX, pendingTapRawY;

    private float pressX, pressY;
    private View pressTarget;
    private Runnable longPressRunnable;
    private boolean pressMoved;
    private boolean longPressConsumed;
    private long lastTapDownTime;
    private float lastTapDownX, lastTapDownY;

    private boolean suppressSpansChanged;

    RichEditorHistory history;

    public RichEditorListView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, Delegate delegate) {
        this(context, currentAccount, resourcesProvider, delegate, new RichEditorListView[1]);
    }

    private RichEditorListView(Context context, int currentAccount, Theme.ResourcesProvider resourcesProvider, Delegate delegate, RichEditorListView[] self) {
        super(
            context, currentAccount, 0, false,
            (items, adapter) -> { if (self[0] != null) self[0].fillItems(items, adapter); },
            (item, view, position, x, y) -> { if (self[0] != null) self[0].onItemClick(item, view, position, x, y); },
            null, resourcesProvider
        );
        self[0] = this;
        this.currentAccount = currentAccount;
        this.resourcesProvider = resourcesProvider;
        this.delegate = delegate;

        adapter.setApplyBackground(false);
        setClipToPadding(false);
        setClipChildren(false);
        listenReorder(this::onRowsReordered);
        setReorderLongPressEnabled(false);
        allowReorder(true);

        textSelectionHelper = new TextSelectionHelper.ArticleTextSelectionHelper() {
            @Override
            protected void onOffsetChanged() {
                super.onOffsetChanged();
                delegate.onSelectionChanged();
            }

            @Override
            protected boolean canCut() { return true; }

            @Override
            protected boolean canPaste() { return true; }

            @Override
            protected boolean forceShowSelectAll() { return !isWholeDocumentSelected(); }

            @Override
            protected boolean onSelectAllOverride() {
                if (expandSelectionToWholeCurrentBlock()) {
                    return true;
                }
                return tryEscalateSelectAll();
            }

            @Override
            public int getParentTopPadding() { return RichEditorListView.this.getPaddingTop(); }

            @Override
            public int getParentBottomPadding() { return RichEditorListView.this.getPaddingBottom(); }

            @Override
            protected boolean onCopyOverride() {
                copyHelperSelection();
                return true;
            }

            @Override
            protected void onCutAction() {
                cutHelperSelection();
            }

            @Override
            protected void onPasteAction() {
                pasteAtHelperSelection();
            }

            @Override
            protected void onTapToDismiss(float rawX, float rawY) {
                pendingTapDismiss = true;
                pendingTapRawX = rawX;
                pendingTapRawY = rawY;
            }
        };
        textSelectionHelper.setParentView(this);
        textSelectionHelper.layoutManager = layoutManager;
        textSelectionOverlay = textSelectionHelper.getOverlayView(context);
        AndroidUtilities.removeFromParent(textSelectionOverlay);

        textSelectionHelper.setCallback(new TextSelectionHelper.Callback() {
            @Override
            public void onStateChanged(boolean isSelected) {
                delegate.onSelectionChanged();
                if (isSelected) {
                    restoreFocusCell = textSelectionHelper.getAnchorCell();
                    restoreFocusOffset = textSelectionHelper.getAnchorOffset();
                    restoreFocusChildPosition = textSelectionHelper.getAnchorChildPosition();
                    setEditTextsLocked(true);
                    hideEditTextActionModes();
                    finishEditTextActionModes();
                } else {
                    final int cell = restoreFocusCell;
                    final int off = restoreFocusOffset;
                    final int childPos = restoreFocusChildPosition;
                    restoreFocusCell = -1;
                    restoreFocusOffset = -1;
                    restoreFocusChildPosition = 0;
                    final boolean tapDismiss = pendingTapDismiss;
                    final float tapX = pendingTapRawX, tapY = pendingTapRawY;
                    pendingTapDismiss = false;
                    setEditTextsLocked(false);
                    finishEditTextActionModes();
                    if (tapDismiss) {
                        post(() -> {
                            if (!restoreFocusAtScreenPoint(tapX, tapY) && cell >= 0) {
                                restoreFocusAt(cell, childPos, off);
                            }
                        });
                    } else if (cell >= 0) {
                        post(() -> restoreFocusAt(cell, childPos, off));
                    } else {
                        final View focused = findFocus();
                        if (focused instanceof RichEditText) {
                            post(((RichEditText) focused)::requestEditFocusRebuild);
                        }
                    }
                }
            }
        });

        addOnScrollListener(new RecyclerListView.OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                if (delegate != null) delegate.onListScrolled(dy);
                textSelectionHelper.onParentScrolled();
                hideKeyboardIfFocusScrolledAway();
            }

            @Override
            public void onScrollStateChanged(RecyclerView recyclerView, int newState) {
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    textSelectionHelper.stopScrolling();
                }
            }
        });

        history = new RichEditorHistory(historyDelegate);
    }

    private int lastListHeight;

    private boolean isInList(View f) {
        return f != null && findContainingItemView(f) != null;
    }

    private RichEditText focusedEditText;

    private final ViewTreeObserver.OnGlobalFocusChangeListener imeFocusListener = (oldF, newF) -> {
        doNotDetachViews(isInList(newF));
        if (newF instanceof RichEditText) {
            focusedEditText = (RichEditText) newF;
        }
    };

    private void hideKeyboardIfFocusScrolledAway() {
        final RichEditText et = focusedEditText;
        if (et == null) return;
        final View child = findContainingItemView(et);
        // require child != null: a transient detach during the keyboard resize shouldn't close the keyboard
        final boolean gone = child != null && (child.getBottom() <= 0 || child.getTop() >= getHeight());
        if (gone) {
            focusedEditText = null;
            et.clearFocus();
            AndroidUtilities.hideKeyboard(this);
        }
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        final int h = b - t;
        final boolean shrunk = lastListHeight > 0 && h < lastListHeight;
        super.onLayout(changed, l, t, r, b);
        if (shrunk) {
            // keyboard opened: if the focused field is below the shrunk viewport, reveal it (keeps the field on screen
            // so it retains focus and the keyboard stays open)
            final View focused = findFocus();
            final View child = focused == null ? null : findContainingItemView(focused);
            if (child != null) {
                final int overflow = child.getBottom() + AndroidUtilities.dp(8) - (h - getPaddingBottom());
                if (overflow > 0) {
                    post(() -> scrollBy(0, overflow));
                }
            }
        }
        lastListHeight = h;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(imeFocusListener);
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        getViewTreeObserver().removeOnGlobalFocusChangeListener(imeFocusListener);
        doNotDetachViews(false);
    }

    public View getOverlayView() {
        return textSelectionOverlay;
    }

    @Override
    protected void onLayoutUpdate() {
        super.onLayoutUpdate();
        if (delegate != null) delegate.onListLayoutUpdated();
    }

    public void seedEmptyArticle() {
        rows.clear();
        rows.add(new BlockRow(new TL_iv.pageBlockHeading1()));
        rows.add(new BlockRow(new TL_iv.pageBlockParagraph()));
        adapter.update(false);
    }

    public void clearContent() {
        destroy();
        loadedRichMessage = null;
        pendingMediaRow = null;
        pendingInsertRow = null;
        history = new RichEditorHistory(historyDelegate);
        seedEmptyArticle();
        history.resetBaseline(); // baseline = seeded content, not the empty state captured above
        if (delegate != null) delegate.onHistoryChanged();
    }

    public void resetHistoryBaseline() { if (history != null) history.resetBaseline(); }
    public void undo() { if (history != null) history.undo(); }
    public void redo() { if (history != null) history.redo(); }
    public boolean canUndo() { return history != null && history.canUndo(); }
    public boolean canRedo() { return history != null && history.canRedo(); }

    public boolean isInSelectionMode() {
        return textSelectionHelper != null && textSelectionHelper.isInSelectionMode();
    }

    private ArrayList<BlockRow> initialSelectionRows;

    public void setInitialText(CharSequence text) {
        if (text == null) return;
        initialSelectionRows = new ArrayList<>();
        final int start = rows.size();
        flattenBlocks(rows, RichMessageConvert.blocksFromCharSequence(text), quoteAuthors);
        for (int i = start; i < rows.size(); i++) {
            initialSelectionRows.add(rows.get(i));
        }
        adapter.update(false);
    }

    private int[] mapInitialOffset(int pos) {
        int cum = 0;
        for (int i = 0; i < initialSelectionRows.size(); i++) {
            final int len = RichTextCell.readPlainText(initialSelectionRows.get(i).block).length();
            if (pos <= cum + len) {
                return new int[] { i, pos - cum };
            }
            cum += len + 1;
        }
        final int last = initialSelectionRows.size() - 1;
        return new int[] { last, RichTextCell.readPlainText(initialSelectionRows.get(last).block).length() };
    }

    public void applyInitialSelection(int start, int end) {
        if (!applyInitialSelectionInternal(start, end)) {
            post(() -> applyInitialSelectionInternal(start, end));
        }
    }

    private boolean applyInitialSelectionInternal(int start, int end) {
        if (initialSelectionRows == null || initialSelectionRows.isEmpty()) return true;
        final int s = Math.max(0, Math.min(start, end));
        final int e = Math.max(0, Math.max(start, end));
        final int[] sp = mapInitialOffset(s);
        final int[] ep = mapInitialOffset(e);
        final BlockRow sRow = initialSelectionRows.get(sp[0]);

        if (sp[0] == ep[0] || s == e) {
            final View v = findViewByItemObject(sRow);
            if (!(v instanceof RichTextCell)) return false;
            final RichTextCell c = (RichTextCell) v;
            c.requestEditFocus();
            final RichEditText et = c.getEditText();
            final int len = et.length();
            et.setSelection(Math.min(sp[1], len), Math.min(ep[1], len));
            return true;
        }

        final View sv = findViewByItemObject(sRow);
        final View ev = findViewByItemObject(initialSelectionRows.get(ep[0]));
        if (!(sv instanceof RichTextCell) || !(ev instanceof RichTextCell)) return false;
        for (int i = 0; i < rows.size(); i++) {
            textSelectionHelper.cacheText(i, RichTextCell.readPlainText(rows.get(i).block), null);
        }
        final int sLen = ((RichTextCell) sv).getEditText().length();
        final int seedEnd = sp[1] < sLen ? sLen : Math.max(0, sLen - 1);
        if (!textSelectionHelper.selectRangeOf((RichTextCell) sv, sp[1], seedEnd)) {
            ((RichTextCell) sv).requestEditFocus();
            return true;
        }
        textSelectionHelper.extendSelectionTo((RichTextCell) ev, ep[1]);
        return true;
    }

    public void loadRichMessage(TL_iv.RichMessage richMessage) {
        if (richMessage == null) return;
        loadedRichMessage = richMessage;
        flattenBlocks(rows, richMessage.blocks, quoteAuthors);
        normalizeNestedQuotes();
        for (int i = 0; i < rows.size(); i++) resolveLoadedMedia(rows.get(i));
        adapter.update(false);
    }

    public void loadHtml(String html) {
        loadHtml(null, html, null);
    }

    public void loadHtml(CharSequence before, String html, CharSequence after) {
        if (html == null) return;
        if (!TextUtils.isEmpty(before)) {
            flattenBlocks(rows, RichMessageConvert.blocksFromCharSequence(before), quoteAuthors);
        }
        final List<BlockRow> parsed = resolvePastedMedia(RichHtml.parse(html, quoteAuthors));
        if (parsed != null) rows.addAll(parsed);
        if (!TextUtils.isEmpty(after)) {
            flattenBlocks(rows, RichMessageConvert.blocksFromCharSequence(after), quoteAuthors);
        }
        normalizeNestedQuotes();
        adapter.update(false);
    }

    public boolean isSimpleConvertible() {
        return hasAnyText() && !RichMessageConvert.isLossy(rows, quoteAuthors);
    }

    public boolean isLossy() {
        return RichMessageConvert.isLossy(rows, quoteAuthors);
    }

    public CharSequence toSimpleMessage() {
        return RichMessageConvert.rowsToCharSequence(rows);
    }

    public void convertToSimple() {
        if (history != null) history.record();
        final CharSequence simple = RichMessageConvert.rowsToSimpleMessage(rows);
        destroy();
        rows.clear();
        quoteAuthors.clear();
        loadedRichMessage = null;
        flattenBlocks(rows, RichMessageConvert.blocksFromCharSequence(simple), quoteAuthors);
        adapter.update(false);
        if (history != null) history.record();
        if (delegate != null) {
            delegate.onContentChanged();
            delegate.onHistoryChanged();
        }
    }

    public void addRichMessage(TL_iv.RichMessage richMessage) {
        if (richMessage == null || richMessage.blocks == null || richMessage.blocks.isEmpty()) return;
        if (history != null) history.flush();
        if (loadedRichMessage == null) {
            loadedRichMessage = richMessage;
        } else {
            if (richMessage.photos != null) loadedRichMessage.photos.addAll(richMessage.photos);
            if (richMessage.documents != null) loadedRichMessage.documents.addAll(richMessage.documents);
        }
        final ArrayList<BlockRow> newRows = new ArrayList<>();
        flattenBlocks(newRows, richMessage.blocks, quoteAuthors);
        if (newRows.isEmpty()) return;
        for (int i = 0; i < newRows.size(); i++) resolveLoadedMedia(newRows.get(i));

        int insertAt = rows.size();
        final BlockRow focused = findFocusedRow();
        if (focused != null) {
            final int idx = rows.indexOf(focused);
            if (idx >= 0) {
                if (focused.block instanceof TL_iv.pageBlockParagraph
                        && RichTextCell.readPlainText(focused.block).isEmpty()) {
                    rows.remove(idx);
                    insertAt = idx;
                } else {
                    insertAt = idx + 1;
                }
            }
        }
        rows.addAll(insertAt, newRows);
        normalizeNestedQuotes();
        renumberAllRuns();
        adapter.update(false);
        if (history != null) history.record();
        if (delegate != null) delegate.onContentChanged();
    }

    public void destroy() {
        if (textSelectionHelper != null) {
            textSelectionHelper.clear(true);
        }
        exitCellSelectionMode();
        hideEditTextActionModes();
        for (RichMediaUploader u : uploaders.values()) {
            u.cancel();
        }
        uploaders.clear();
        for (RichMediaConverter c : converters.values()) {
            c.cancel();
        }
        converters.clear();
    }

    private TLRPC.Document findLoadedDocument(long id) {
        if (id == 0) return null;
        for (int i = 0; i < rows.size(); i++) {
            for (MediaUploadState m : mediasOf(rows.get(i))) {
                if (m != null && m.document != null && m.document.id == id) return m.document;
            }
        }
        if (loadedRichMessage != null && loadedRichMessage.documents != null) {
            for (TLRPC.Document d : loadedRichMessage.documents) {
                if (d != null && d.id == id) return d;
            }
        }
        return RichMediaClipboard.document(id);
    }

    private TLRPC.Photo findLoadedPhoto(long id) {
        if (id == 0) return null;
        for (int i = 0; i < rows.size(); i++) {
            for (MediaUploadState m : mediasOf(rows.get(i))) {
                if (m != null && m.photo != null && m.photo.id == id) return m.photo;
            }
        }
        if (loadedRichMessage != null && loadedRichMessage.photos != null) {
            for (TLRPC.Photo p : loadedRichMessage.photos) {
                if (p != null && p.id == id) return p;
            }
        }
        return RichMediaClipboard.photo(id);
    }

    private void resolveLoadedMedia(BlockRow row) {
        if (loadedRichMessage == null || row == null || row.block == null) return;
        if (isGallery(row.block)) {
            final ArrayList<TL_iv.PageBlock> items = galleryItems(row.block);
            row.medias = new ArrayList<>();
            if (items != null) {
                for (int i = 0; i < items.size(); i++) {
                    MediaUploadState ms = resolveBlockMedia(items.get(i));
                    row.medias.add(ms != null ? ms : new MediaUploadState());
                }
            }
        } else {
            final MediaUploadState ms = resolveBlockMedia(row.block);
            if (ms != null) row.media = ms;
        }
    }

    private MediaUploadState resolveBlockMedia(TL_iv.PageBlock block) {
        if (block instanceof TL_iv.pageBlockAudio) {
            final TLRPC.Document doc = findLoadedDocument(((TL_iv.pageBlockAudio) block).audio_id);
            if (doc == null) return null;
            final MediaUploadState media = new MediaUploadState();
            media.isAudio = true;
            media.state = MediaUploadState.STATE_DONE;
            media.document = doc;
            media.audioDisplayDocument = doc;
            return media;
        } else if (block instanceof TL_iv.pageBlockVideo) {
            final TLRPC.Document doc = findLoadedDocument(((TL_iv.pageBlockVideo) block).video_id);
            if (doc == null) return null;
            final MediaUploadState media = new MediaUploadState();
            media.isVideo = true;
            media.state = MediaUploadState.STATE_DONE;
            media.document = doc;
            media.hasSpoiler = ((TL_iv.pageBlockVideo) block).spoiler;
            for (int i = 0; i < doc.attributes.size(); i++) {
                if (doc.attributes.get(i) instanceof TLRPC.TL_documentAttributeVideo) {
                    final TLRPC.TL_documentAttributeVideo a = (TLRPC.TL_documentAttributeVideo) doc.attributes.get(i);
                    media.width = a.w;
                    media.height = a.h;
                    media.duration = (int) a.duration;
                    break;
                }
            }
            return media;
        } else if (block instanceof TL_iv.pageBlockPhoto) {
            final TLRPC.Photo photo = findLoadedPhoto(((TL_iv.pageBlockPhoto) block).photo_id);
            if (photo == null) return null;
            final MediaUploadState media = new MediaUploadState();
            media.state = MediaUploadState.STATE_DONE;
            media.photo = photo;
            media.hasSpoiler = ((TL_iv.pageBlockPhoto) block).spoiler;
            final TLRPC.PhotoSize big = org.telegram.messenger.FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
            if (big != null) {
                media.width = big.w;
                media.height = big.h;
            }
            return media;
        }
        return null;
    }

    private List<BlockRow> resolvePastedMedia(List<BlockRow> blocks) {
        if (blocks == null) return null;
        final ArrayList<BlockRow> out = new ArrayList<>(blocks.size());
        for (BlockRow row : blocks) {
            if (isGallery(row.block)) {
                final ArrayList<TL_iv.PageBlock> items = galleryItems(row.block);
                final ArrayList<TL_iv.PageBlock> keptItems = new ArrayList<>();
                final ArrayList<MediaUploadState> medias = new ArrayList<>();
                if (items != null) {
                    for (TL_iv.PageBlock item : items) {
                        final MediaUploadState m = resolveBlockMedia(item);
                        if (m == null) continue;
                        keptItems.add(item);
                        medias.add(m);
                    }
                    items.clear();
                    items.addAll(keptItems);
                }
                if (medias.isEmpty()) continue;
                row.medias = medias;
                out.add(row);
            } else if (row.block instanceof TL_iv.pageBlockPhoto
                    || row.block instanceof TL_iv.pageBlockVideo
                    || row.block instanceof TL_iv.pageBlockAudio) {
                final MediaUploadState m = resolveBlockMedia(row.block);
                if (m == null) continue;
                row.media = m;
                out.add(row);
            } else {
                out.add(row);
            }
        }
        return out;
    }

    static void flattenBlocks(ArrayList<BlockRow> out, ArrayList<TL_iv.PageBlock> blocks) {
        flattenBlocks(out, blocks, null);
    }

    static void flattenBlocks(ArrayList<BlockRow> out, ArrayList<TL_iv.PageBlock> blocks, Map<Long, TL_iv.RichText> authors) {
        if (blocks == null) return;
        for (TL_iv.PageBlock block : blocks) {
            if (block instanceof TL_iv.pageBlockList || block instanceof TL_iv.pageBlockOrderedList) {
                expandListBlock(out, block, 1);
            } else if (block instanceof TL_iv.pageBlockDetails) {
                flattenDetails(out, (TL_iv.pageBlockDetails) block, authors);
            } else if (block instanceof TL_iv.pageBlockBlockquoteBlocks) {
                expandBlockquoteBlocks(out, (TL_iv.pageBlockBlockquoteBlocks) block, authors);
            } else {
                out.add(new BlockRow(block));
            }
        }
    }

    static void expandBlockquoteBlocks(ArrayList<BlockRow> out, TL_iv.pageBlockBlockquoteBlocks src, Map<Long, TL_iv.RichText> authors) {
        final long qid = RichContainer.newId();
        final int start = out.size();
        flattenBlocks(out, src.blocks, authors);
        for (int i = start; i < out.size(); i++) {
            out.get(i).quoteIds.add(0, qid);
        }
        if (authors != null && src.caption != null && !(src.caption instanceof TL_iv.textEmpty)) {
            authors.put(qid, src.caption);
        }
    }

    private static TL_iv.pageBlockBlockquote blockquoteFromBlocks(TL_iv.pageBlockBlockquoteBlocks src) {
        final TL_iv.pageBlockBlockquote bq = new TL_iv.pageBlockBlockquote();
        final ArrayList<TL_iv.RichText> lines = new ArrayList<>();
        if (src.blocks != null) {
            for (TL_iv.PageBlock b : src.blocks) appendQuoteLines(b, lines);
        }
        bq.text = joinRichLines(lines);
        bq.caption = src.caption != null ? src.caption : new TL_iv.textEmpty();
        return bq;
    }

    private static void appendQuoteLines(TL_iv.PageBlock b, ArrayList<TL_iv.RichText> out) {
        if (b == null) return;
        if (isHeading(b)) {
            final TL_iv.textBold bold = new TL_iv.textBold();
            bold.text = textOrEmpty(b.text);
            out.add(bold);
        } else if (b instanceof TL_iv.pageBlockParagraph
                || b instanceof TL_iv.pageBlockPreformatted
                || b instanceof TL_iv.pageBlockFooter) {
            out.add(textOrEmpty(b.text));
        } else if (b instanceof TL_iv.pageBlockList) {
            for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) b).items) {
                if (item.checkbox) continue;
                final TL_iv.RichText t = listItemText(item);
                if (t != null) out.add(prefixedLine("-  ", t));
            }
        } else if (b instanceof TL_iv.pageBlockOrderedList) {
            int n = 1;
            for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) b).items) {
                if (item.checkbox) continue;
                final TL_iv.RichText t = orderedItemText(item);
                if (t != null) out.add(prefixedLine((n++) + ". ", t));
            }
        }
    }

    private static TL_iv.RichText listItemText(TL_iv.PageListItem item) {
        if (item instanceof TL_iv.TL_pageListItemText) return textOrEmpty(((TL_iv.TL_pageListItemText) item).text);
        if (item instanceof TL_iv.TL_pageListItemBlocks) return textOrEmpty(firstParagraphText(((TL_iv.TL_pageListItemBlocks) item).blocks));
        return null;
    }

    private static TL_iv.RichText orderedItemText(TL_iv.PageListOrderedItem item) {
        if (item instanceof TL_iv.TL_pageListOrderedItemText) return textOrEmpty(((TL_iv.TL_pageListOrderedItemText) item).text);
        if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) return textOrEmpty(firstParagraphText(((TL_iv.TL_pageListOrderedItemBlocks) item).blocks));
        return null;
    }

    private static TL_iv.RichText prefixedLine(String prefix, TL_iv.RichText text) {
        final TL_iv.textConcat concat = new TL_iv.textConcat();
        final TL_iv.textPlain p = new TL_iv.textPlain();
        p.text = prefix;
        concat.texts.add(p);
        concat.texts.add(text);
        return concat;
    }

    private static TL_iv.RichText textOrEmpty(TL_iv.RichText t) {
        return t != null ? t : new TL_iv.textEmpty();
    }

    private static TL_iv.RichText joinRichLines(ArrayList<TL_iv.RichText> lines) {
        if (lines.isEmpty()) return new TL_iv.textEmpty();
        if (lines.size() == 1) return lines.get(0);
        final TL_iv.textConcat concat = new TL_iv.textConcat();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                final TL_iv.textPlain nl = new TL_iv.textPlain();
                nl.text = "\n";
                concat.texts.add(nl);
            }
            concat.texts.add(lines.get(i));
        }
        return concat;
    }

    public static ArrayList<BlockRow> flattenForCopy(TL_iv.RichMessage msg) {
        return flattenForCopy(msg, null);
    }

    public static ArrayList<BlockRow> flattenForCopy(TL_iv.RichMessage msg, Map<Long, TL_iv.RichText> authors) {
        final ArrayList<BlockRow> out = new ArrayList<>();
        if (msg != null) flattenBlocks(out, msg.blocks, authors);
        return out;
    }

    private static void flattenDetails(ArrayList<BlockRow> out, TL_iv.pageBlockDetails details,
                                       Map<Long, TL_iv.RichText> authors) {
        if (details.title == null) details.title = new TL_iv.textEmpty();
        out.add(new BlockRow(details));
        final int childStart = out.size();
        flattenBlocks(out, details.blocks, authors);
        if (out.size() == childStart) {
            out.add(new BlockRow(new TL_iv.pageBlockParagraph()));
        }
        out.add(newDetailsEndRow());
    }

    private static BlockRow newDetailsEndRow() {
        final BlockRow end = new BlockRow(new TL_iv.pageBlockParagraph());
        end.detailsEnd = true;
        return end;
    }

    static boolean isDetailsHeader(BlockRow row) {
        return row != null && row.block instanceof TL_iv.pageBlockDetails;
    }

    private int matchingDetailsEnd(int headerIdx) {
        int depth = 1;
        for (int i = headerIdx + 1; i < rows.size(); i++) {
            final BlockRow r = rows.get(i);
            if (isDetailsHeader(r)) depth++;
            else if (r.detailsEnd) {
                depth--;
                if (depth == 0) return i;
            }
        }
        return rows.size();
    }

    private static void expandListBlock(ArrayList<BlockRow> out, TL_iv.PageBlock listBlock, int level) {
        final boolean ordered = listBlock instanceof TL_iv.pageBlockOrderedList;
        int counter = 1;
        if (ordered) {
            for (TL_iv.PageListOrderedItem item : ((TL_iv.pageBlockOrderedList) listBlock).items) {
                if (item instanceof TL_iv.TL_pageListOrderedItemText) {
                    addListItemRow(out, ((TL_iv.TL_pageListOrderedItemText) item).text, level, counter, item.checkbox, item.checked);
                } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
                    expandItemBlocks(out, ((TL_iv.TL_pageListOrderedItemBlocks) item).blocks, level, true, counter, item.checkbox, item.checked);
                } else {
                    continue;
                }
                counter++;
            }
        } else {
            for (TL_iv.PageListItem item : ((TL_iv.pageBlockList) listBlock).items) {
                if (item instanceof TL_iv.TL_pageListItemText) {
                    addListItemRow(out, ((TL_iv.TL_pageListItemText) item).text, level, 0, item.checkbox, item.checked);
                } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
                    expandItemBlocks(out, ((TL_iv.TL_pageListItemBlocks) item).blocks, level, false, 0, item.checkbox, item.checked);
                } else {
                    continue;
                }
                counter++;
            }
        }
    }

    private static void addListItemRow(ArrayList<BlockRow> out, TL_iv.RichText text, int level, int num, boolean checkbox, boolean checked) {
        TL_iv.pageBlockParagraph para = new TL_iv.pageBlockParagraph();
        para.text = text != null ? text : new TL_iv.textEmpty();
        BlockRow row = new BlockRow(para, level, num);
        row.checkbox = checkbox;
        row.checked = checked;
        out.add(row);
    }

    private static TL_iv.RichText firstParagraphText(ArrayList<TL_iv.PageBlock> blocks) {
        if (blocks != null && !blocks.isEmpty() && blocks.get(0) instanceof TL_iv.pageBlockParagraph) {
            return ((TL_iv.pageBlockParagraph) blocks.get(0)).text;
        }
        return null;
    }

    private static void expandItemBlocks(ArrayList<BlockRow> out, ArrayList<TL_iv.PageBlock> blocks,
                                         int level, boolean ordered, int num, boolean checkbox, boolean checked) {
        boolean started = false;
        if (blocks != null) {
            for (int i = 0; i < blocks.size(); i++) {
                final TL_iv.PageBlock b = blocks.get(i);
                if (b instanceof TL_iv.pageBlockList || b instanceof TL_iv.pageBlockOrderedList) {
                    if (!started) {
                        addListItemRow(out, null, level, num, checkbox, checked);
                        started = true;
                    }
                    expandListBlock(out, b, level + 1);
                    continue;
                }
                if (!started) {
                    if (b instanceof TL_iv.pageBlockParagraph) {
                        addListItemRow(out, ((TL_iv.pageBlockParagraph) b).text, level, num, checkbox, checked);
                    } else {
                        final BlockRow row = new BlockRow(b, level, num);
                        row.checkbox = checkbox;
                        row.checked = checked;
                        out.add(row);
                    }
                    started = true;
                } else {
                    out.add(new BlockRow(b, level, ordered ? 1 : 0));
                }
            }
        }
        if (!started) {
            addListItemRow(out, null, level, num, checkbox, checked);
        }
    }

    private static int clearMaskFor(int flag) {
        if (flag == RichTextStyle.MONO) return RichTextStyle.SUPPORTED & ~RichTextStyle.MONO;
        int mask = RichTextStyle.MONO;
        if (flag == RichTextStyle.SUBSCRIPT) mask |= RichTextStyle.SUPERSCRIPT;
        else if (flag == RichTextStyle.SUPERSCRIPT) mask |= RichTextStyle.SUBSCRIPT;
        return mask;
    }

    void onFormattingClicked(int flag) {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return;
        if (isTableSelection()) { onFormattingClickedTable(flag); return; }
        if (isCaptionSelection()) { onFormattingClickedCaption(flag); return; }
        if (isQuoteAuthorSelection()) { onFormattingClickedAuthor(flag); return; }
        int sCell = textSelectionHelper.getStartCell();
        int eCell = textSelectionHelper.getEndCell();
        int sOff = textSelectionHelper.getStartOffset();
        int eOff = textSelectionHelper.getEndOffset();
        if (sCell < 0 || eCell < 0 || eCell < sCell || eCell >= itemRows.size()) return;

        boolean add = !isStyleFullyApplied(flag, sCell, sOff, eCell, eOff);

        int clearMask = 0;
        if (add) {
            clearMask = clearMaskFor(flag);
        }

        if (history != null) history.flush();
        boolean changed = false;
        suppressSpansChanged = true;
        for (int i = sCell; i <= eCell; i++) {
            BlockRow row = rowForCell(i);
            if (row == null || !isInlineFormattable(row.block)) continue;
            int len = blockTextLength(i);
            int from = i == sCell ? sOff : 0;
            int to = i == eCell ? eOff : len;
            from = Math.max(0, Math.min(from, len));
            to = Math.max(0, Math.min(to, len));
            if (from >= to) continue;

            RichTextCell cell = cellAt(i);
            if (cell != null) {
                FloatingToolbar.StyleDelegate sd = cell.getStyleDelegate();
                if (add) {
                    if (clearMask != 0) sd.removeStyle(clearMask, from, to);
                    sd.addStyle(flag, from, to);
                } else {
                    sd.removeStyle(flag, from, to);
                }
                cell.getEditText().invalidateEffects();
                cell.getEditText().requestLayout();
                cell.persistStyle();
            } else {
                SpannableStringBuilder sb = new SpannableStringBuilder(RichTextCell.readStyledText(row.block));
                if (add && clearMask != 0) RichTextStyle.setStyle(sb, from, to, clearMask, false);
                RichTextStyle.setStyle(sb, from, to, flag, add);
                RichTextCell.applyStyledTextToBlock(row.block, sb);
            }
            changed = true;
        }
        suppressSpansChanged = false;
        if (changed && history != null) history.record();
        delegate.onSelectionChanged();
        if (changed) refreshSelectionHighlight();
    }

    private void refreshSelectionHighlight() {
        post(() -> {
            if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return;
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child instanceof RichTextCell || child instanceof RichTableCell || child instanceof RichCaptionHost || child instanceof RichDetailsCell) child.invalidate();
            }
            textSelectionHelper.invalidate();
        });
    }

    private void onCellSpansChanged() {
        if (suppressSpansChanged) return;
        if (history != null) history.record();
        delegate.onSelectionChanged();
        refreshSelectionHighlight();
    }

    private RichTextCell singleSelectionCell() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return null;
        int sCell = textSelectionHelper.getStartCell();
        int eCell = textSelectionHelper.getEndCell();
        final BlockRow row = rowForCell(sCell);
        if (sCell != eCell || row == null) return null;
        if (!isFormattable(row.block)) return null;
        return cellAt(sCell);
    }

    void onLinkClicked() {
        if (isTableSelection()) { onLinkClickedTable(); return; }
        if (isCaptionSelection()) { onLinkClickedCaption(); return; }
        if (isQuoteAuthorSelection()) { onLinkClickedAuthor(); return; }
        RichTextCell cell = singleSelectionCell();
        if (cell == null) return;
        int len = cell.getEditText().length();
        int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;

        if (RichTextStyle.hasLink(cell.getEditText().getText(), from, to)) {
            if (history != null) history.flush();
            RichTextStyle.removeLink(cell.getEditText().getText(), from, to);
            cell.getEditText().invalidateEffects();
            cell.persistStyle();
            if (history != null) history.record();
            delegate.onSelectionChanged();
            refreshSelectionHighlight();
        } else {
            if (history != null) history.flush();
            if (RichTextStyle.hasDate(cell.getEditText().getText(), from, to)) {
                RichTextStyle.removeDate(cell.getEditText().getText(), from, to);
                cell.getEditText().invalidateEffects();
                cell.persistStyle();
                if (history != null) history.record();
                refreshSelectionHighlight();
            }
            cell.getEditText().setSelectionOverride(from, to);
            cell.getEditText().makeSelectedUrl();
        }
    }

    void onDateClicked() {
        if (isTableSelection()) { onDateClickedTable(); return; }
        if (isCaptionSelection()) { onDateClickedCaption(); return; }
        if (isQuoteAuthorSelection()) { onDateClickedAuthor(); return; }
        RichTextCell cell = singleSelectionCell();
        if (cell == null) return;
        int len = cell.getEditText().length();
        int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        if (history != null) history.flush();
        if (RichTextStyle.hasLink(cell.getEditText().getText(), from, to)) {
            RichTextStyle.removeLink(cell.getEditText().getText(), from, to);
            cell.getEditText().invalidateEffects();
            cell.persistStyle();
            if (history != null) history.record();
            refreshSelectionHighlight();
        }
        cell.getEditText().setSelectionOverride(from, to);
        cell.getEditText().makeSelectedDate();
    }

    void onMathClicked() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return;
        final RichEditText et;
        final Runnable persist;
        if (isTableSelection()) {
            final int pos = textSelectionHelper.getStartCell();
            final int sChild = textSelectionHelper.getStartChildPosition();
            final int eChild = textSelectionHelper.getEndChildPosition();
            if (sChild != eChild) return;
            et = tableEditText(pos, sChild);
            persist = () -> persistTableCell(pos, sChild);
        } else if (isCaptionSelection()) {
            final int pos = textSelectionHelper.getStartCell();
            et = captionEditText(pos);
            persist = () -> persistCaption(pos);
        } else if (isQuoteAuthorSelection()) {
            final int pos = textSelectionHelper.getStartCell();
            et = quoteAuthorEditText(pos);
            persist = () -> persistQuoteAuthor(pos);
        } else {
            final RichTextCell cell = singleSelectionCell();
            if (cell == null) return;
            et = cell.getEditText();
            persist = cell::persistStyle;
        }
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from > to) return;
        insertInlineMath(et, from, to, persist);
    }

    private void insertInlineMath(RichEditText et, int from, int to, Runnable persist) {
        final String existing = MathSpan.sourceAt(et.getText(), from, to);
        final String prefill = existing != null ? existing : et.getText().subSequence(from, to).toString();
        final float textSize = dp(4 + SharedConfig.fontSize);
        final int color = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        ChatAttachAlertRichLayout.showEditLatexSheet(getContext(), prefill, source -> {
            if (TextUtils.isEmpty(source)) return;
            final MathSpan span = MathSpan.create(source, color, textSize);
            if (span == null) return;
            if (history != null) history.flush();
            if (textSelectionHelper != null) textSelectionHelper.clear();
            et.setLocked(false);
            final SpannableString placeholder = new SpannableString(" ");
            placeholder.setSpan(span, 0, 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            final Editable e = et.getText();
            final int f = Math.max(0, Math.min(from, e.length()));
            final int t = Math.max(f, Math.min(to, e.length()));
            e.replace(f, t, placeholder);
            persist.run();
            if (history != null) history.record();
            delegate.onContentChanged();
            final int caret = Math.min(f + 1, et.length());
            et.requestEditFocus();
            post(() -> {
                et.requestEditFocus();
                et.setSelection(Math.min(caret, et.length()));
            });
        }, resourcesProvider);
    }

    public interface SelectionEdit {
        TL_iv.RichMessage extractRichMessage();
        void replaceWith(TL_iv.RichMessage richMessage);
    }

    public SelectionEdit beginSelectionEdit() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return null;
        if (isQuoteAuthorSelection()) return null;
        int r0 = rows.indexOf(rowForCell(textSelectionHelper.getStartCell()));
        int r1 = rows.indexOf(rowForCell(textSelectionHelper.getEndCell()));
        if (r0 < 0 || r1 < 0) return null;
        if (r0 > r1) { final int t = r0; r0 = r1; r1 = t; }
        for (int i = r0; i <= r1; i++) {
            final BlockRow row = rows.get(i);
            if (row.detailsEnd || isDetailsHeader(row)) return null;
        }
        final int startRowIdx = r0, endRowIdx = r1;
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        return new SelectionEdit() {
            @Override
            public TL_iv.RichMessage extractRichMessage() {
                final BlockRow startRow = rows.get(startRowIdx);
                final BlockRow endRow = rows.get(endRowIdx);
                final TL_iv.PageBlock origStart = startRow.block;
                final TL_iv.PageBlock origEnd = endRow.block;
                final TL_iv.PageBlock startClone = sliceClone(startRow, sOff, startRowIdx == endRowIdx ? eOff : -1);
                final TL_iv.PageBlock endClone = startRowIdx == endRowIdx ? null : sliceClone(endRow, 0, eOff);
                final ArrayList<TL_iv.PageBlock> blocks;
                final ArrayList<TLRPC.Photo> photos;
                final ArrayList<TLRPC.Document> documents;
                if (startClone != null) startRow.block = startClone;
                if (endClone != null) endRow.block = endClone;
                try {
                    blocks = flattenRange(startRowIdx, endRowIdx + 1, false);
                    photos = collectMediaPhotos(startRowIdx, endRowIdx);
                    documents = collectMediaDocuments(startRowIdx, endRowIdx);
                } finally {
                    startRow.block = origStart;
                    endRow.block = origEnd;
                }
                final TL_iv.RichMessage rich = new TL_iv.RichMessage();
                rich.blocks = blocks;
                rich.photos = photos;
                rich.documents = documents;
                return rich;
            }

            @Override
            public void replaceWith(TL_iv.RichMessage rich) {
                if (rich == null) return;
                if (startRowIdx >= rows.size() || endRowIdx >= rows.size()) return;
                if (history != null) history.flush();
                if (textSelectionHelper != null) textSelectionHelper.clear();

                final BlockRow startRow = rows.get(startRowIdx);
                final BlockRow endRow = rows.get(endRowIdx);
                final CharSequence startStyled = isFormattable(startRow.block) ? styledTextOf(startRow) : "";
                final CharSequence endStyled = isFormattable(endRow.block) ? styledTextOf(endRow) : "";
                final CharSequence head = new SpannableStringBuilder(startStyled.subSequence(0, Math.max(0, Math.min(sOff, startStyled.length()))));
                final CharSequence tail = new SpannableStringBuilder(endStyled.subSequence(Math.max(0, Math.min(eOff, endStyled.length())), endStyled.length()));

                final ArrayList<BlockRow> newRows = new ArrayList<>();
                flattenBlocks(newRows, rich.blocks);
                if (newRows.isEmpty()) {
                    final SpannableStringBuilder merged = new SpannableStringBuilder(head);
                    merged.append(tail);
                    final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                    RichTextCell.applyStyledTextToBlock(p, merged);
                    newRows.add(new BlockRow(p, startRow.level, startRow.num));
                } else {
                    if (head.length() > 0) {
                        final BlockRow first = newRows.get(0);
                        if (isFormattable(first.block)) {
                            final SpannableStringBuilder sb = new SpannableStringBuilder(head);
                            sb.append(RichTextCell.readStyledText(first.block));
                            RichTextCell.applyStyledTextToBlock(first.block, sb);
                        } else {
                            final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                            RichTextCell.applyStyledTextToBlock(p, head);
                            newRows.add(0, new BlockRow(p, startRow.level, startRow.num));
                        }
                    }
                    if (tail.length() > 0) {
                        final BlockRow last = newRows.get(newRows.size() - 1);
                        if (isFormattable(last.block)) {
                            final SpannableStringBuilder sb = new SpannableStringBuilder(RichTextCell.readStyledText(last.block));
                            sb.append(tail);
                            RichTextCell.applyStyledTextToBlock(last.block, sb);
                        } else {
                            final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                            RichTextCell.applyStyledTextToBlock(p, tail);
                            newRows.add(new BlockRow(p, endRow.level, endRow.num));
                        }
                    }
                }

                if (loadedRichMessage == null) {
                    loadedRichMessage = rich;
                } else {
                    if (rich.photos != null) loadedRichMessage.photos.addAll(rich.photos);
                    if (rich.documents != null) loadedRichMessage.documents.addAll(rich.documents);
                }
                for (int i = 0; i < newRows.size(); i++) resolveLoadedMedia(newRows.get(i));

                for (int i = endRowIdx; i >= startRowIdx; i--) rows.remove(i);
                rows.addAll(startRowIdx, newRows);
                renumberAllRuns();
                adapter.update(false);
                if (history != null) history.record();
                delegate.onContentChanged();

                final BlockRow focusRow = newRows.isEmpty() ? null : newRows.get(newRows.size() - 1);
                post(() -> {
                    final View v = focusRow == null ? null : findViewByItemObject(focusRow);
                    if (v instanceof RichTextCell) {
                        final RichTextCell cell = (RichTextCell) v;
                        cell.requestEditFocus();
                        cell.getEditText().setSelection(cell.getEditText().length());
                    }
                });
            }
        };
    }

    private CharSequence styledTextOf(BlockRow row) {
        final View v = findViewByItemObject(row);
        if (v instanceof RichTextCell) return ((RichTextCell) v).getEditText().getText();
        return RichTextCell.readStyledText(row.block);
    }

    private TL_iv.PageBlock sliceClone(BlockRow row, int from, int to) {
        if (!isFormattable(row.block)) return null;
        final CharSequence styled = styledTextOf(row);
        final int len = styled.length();
        final int f = Math.max(0, Math.min(from, len));
        final int t = to < 0 ? len : Math.max(0, Math.min(to, len));
        final CharSequence sliced = new SpannableStringBuilder(styled.subSequence(Math.min(f, t), Math.max(f, t)));
        final TL_iv.PageBlock clone = cloneBlock(row.block);
        RichTextCell.applyStyledTextToBlock(clone, sliced);
        return clone;
    }

    private ArrayList<TLRPC.Photo> collectMediaPhotos(int from, int to) {
        final ArrayList<TLRPC.Photo> out = new ArrayList<>();
        final HashSet<Long> seen = new HashSet<>();
        for (int i = from; i <= to && i < rows.size(); i++) {
            for (MediaUploadState ms : mediasOf(rows.get(i))) {
                if (ms.isReady() && ms.photo != null && seen.add(ms.photo.id)) out.add(ms.photo);
            }
        }
        return out;
    }

    private ArrayList<TLRPC.Document> collectMediaDocuments(int from, int to) {
        final ArrayList<TLRPC.Document> out = new ArrayList<>();
        final HashSet<Long> seen = new HashSet<>();
        for (int i = from; i <= to && i < rows.size(); i++) {
            for (MediaUploadState ms : mediasOf(rows.get(i))) {
                if (ms.isReady() && ms.document != null && seen.add(ms.document.id)) out.add(ms.document);
            }
        }
        return out;
    }

    private static TL_iv.PageBlock cloneBlock(TL_iv.PageBlock src) {
        if (src != null) {
            try {
                ensureSerializable(src);
                final SerializedData data = new SerializedData(src.getObjectSize());
                src.serializeToStream(data);
                final SerializedData in = new SerializedData(data.toByteArray());
                final TL_iv.PageBlock copy = TL_iv.PageBlock.TLdeserialize(in, in.readInt32(true), true);
                data.cleanup();
                in.cleanup();
                if (copy != null) return copy;
            } catch (Throwable t) {
                FileLog.e(t);
            }
        }
        final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
        p.text = new TL_iv.textEmpty();
        return p;
    }

    private static void ensureSerializable(TL_iv.PageBlock b) {
        if (b == null) return;
        if (b.text == null) b.text = new TL_iv.textEmpty();
        if (b instanceof TL_iv.pageBlockPreformatted && ((TL_iv.pageBlockPreformatted) b).language == null) {
            ((TL_iv.pageBlockPreformatted) b).language = "";
        } else if (b instanceof TL_iv.pageBlockBlockquote && ((TL_iv.pageBlockBlockquote) b).caption == null) {
            ((TL_iv.pageBlockBlockquote) b).caption = new TL_iv.textEmpty();
        } else if (b instanceof TL_iv.pageBlockPullquote && ((TL_iv.pageBlockPullquote) b).caption == null) {
            ((TL_iv.pageBlockPullquote) b).caption = new TL_iv.textEmpty();
        }
    }

    boolean isLinkApplied(int sCell, int sOff, int eCell, int eOff) {
        CharSequence cs = singleSelectionText(sCell, sOff, eCell, eOff);
        if (cs == null) return false;
        int from = Math.max(0, Math.min(sOff, eOff));
        int to = Math.max(sOff, eOff);
        return RichTextStyle.hasLink(cs, from, to);
    }

    boolean isDateApplied(int sCell, int sOff, int eCell, int eOff) {
        CharSequence cs = singleSelectionText(sCell, sOff, eCell, eOff);
        if (cs == null) return false;
        int from = Math.max(0, Math.min(sOff, eOff));
        int to = Math.max(sOff, eOff);
        return RichTextStyle.hasDate(cs, from, to);
    }

    private CharSequence singleSelectionText(int sCell, int sOff, int eCell, int eOff) {
        if (sCell != eCell) return null;
        if (isQuoteAuthorSelection()) {
            final RichEditText et = quoteAuthorEditText(sCell);
            return et != null ? et.getText() : null;
        }
        final BlockRow srow = rowForCell(sCell);
        if (srow == null || !isFormattable(srow.block)) return null;
        int len = blockTextLength(sCell);
        int from = Math.max(0, Math.min(Math.min(sOff, eOff), len));
        int to = Math.max(0, Math.min(Math.max(sOff, eOff), len));
        if (from >= to) return null;
        RichTextCell cell = cellAt(sCell);
        return cell != null ? cell.getEditText().getText() : RichTextCell.readStyledText(srow.block);
    }

    boolean isStyleFullyApplied(int flag, int sCell, int sOff, int eCell, int eOff) {
        if (isQuoteAuthorSelection()) {
            final RichEditText et = quoteAuthorEditText(sCell);
            if (et == null) return false;
            final int len = et.length();
            final int from = Math.max(0, Math.min(Math.min(sOff, eOff), len));
            final int to = Math.max(0, Math.min(Math.max(sOff, eOff), len));
            return from < to && (et.getCurrentStyle(from, to) & flag) != 0;
        }
        boolean anyFormattable = false;
        for (int i = sCell; i <= eCell; i++) {
            BlockRow row = rowForCell(i);
            if (row == null || !isInlineFormattable(row.block)) continue;
            int len = blockTextLength(i);
            int from = i == sCell ? sOff : 0;
            int to = i == eCell ? eOff : len;
            from = Math.max(0, Math.min(from, len));
            to = Math.max(0, Math.min(to, len));
            if (from >= to) continue;
            anyFormattable = true;
            RichTextCell cell = cellAt(i);
            boolean covered;
            if (cell != null) {
                covered = (cell.getStyleDelegate().getCurrentStyle(from, to) & flag) != 0;
            } else {
                covered = RichTextStyle.hasStyle(RichTextCell.readStyledText(rowForCell(i).block), from, to, flag);
            }
            if (!covered) return false;
        }
        return anyFormattable;
    }

    private int blockTextLength(int adapterPos) {
        RichTextCell cell = cellAt(adapterPos);
        if (cell != null) return cell.getEditText().length();
        final BlockRow row = rowForCell(adapterPos);
        return row == null ? 0 : RichTextCell.readPlainText(row.block).length();
    }

    private static boolean isFormattable(TL_iv.PageBlock b) {
        return !isNonText(b) && !(b instanceof TL_iv.pageBlockDetails);
    }

    private static boolean isInlineFormattable(TL_iv.PageBlock b) {
        return isFormattable(b) && !(b instanceof TL_iv.pageBlockPreformatted);
    }

    boolean isTableSelection() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return false;
        final int s = textSelectionHelper.getStartCell();
        final int e = textSelectionHelper.getEndCell();
        final BlockRow row = rowForCell(s);
        return s == e && row != null && row.block instanceof TL_iv.pageBlockTable;
    }

    RichEditText tableEditText(int adapterPos, int childPos) {
        final View v = selectableAt(adapterPos);
        if (!(v instanceof RichTableCell)) return null;
        return ((RichTableCell) v).editTextForChildPos(childPos);
    }

    private void persistTableCell(int adapterPos, int childPos) {
        final View v = selectableAt(adapterPos);
        if (!(v instanceof RichTableCell)) return;
        final RichTableCell t = (RichTableCell) v;
        if (childPos == t.titleChildPos()) { t.persistTitleFromEditor(); return; }
        final TL_iv.pageTableCell mc = t.anchorForChildPos(childPos);
        if (mc == null) return;
        final RichTableCellHost host = t.getGrid().hostForAnchor(mc);
        if (host != null) TableModel.applyStyledText(mc, host.editText.getText());
    }

    private boolean tableSelectionHasFormattable() {
        final int pos = textSelectionHelper.getStartCell();
        final int sChild = textSelectionHelper.getStartChildPosition();
        final int eChild = textSelectionHelper.getEndChildPosition();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        for (int cp = sChild; cp <= eChild; cp++) {
            final RichEditText et = tableEditText(pos, cp);
            if (et == null) continue;
            final int len = et.length();
            int from = cp == sChild ? sOff : 0;
            int to = cp == eChild ? eOff : len;
            if (sChild == eChild) { from = Math.min(sOff, eOff); to = Math.max(sOff, eOff); }
            from = Math.max(0, Math.min(from, len));
            to = Math.max(0, Math.min(to, len));
            if (from < to) return true;
        }
        return false;
    }

    private void onFormattingClickedTable(int flag) {
        final int pos = textSelectionHelper.getStartCell();
        final int sChild = textSelectionHelper.getStartChildPosition();
        final int eChild = textSelectionHelper.getEndChildPosition();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();

        final boolean add = !isStyleFullyAppliedTable(flag, pos, sChild, sOff, eChild, eOff);
        int clearMask = 0;
        if (add) {
            clearMask = clearMaskFor(flag);
        }

        if (history != null) history.flush();
        boolean changed = false;
        suppressSpansChanged = true;
        for (int cp = sChild; cp <= eChild; cp++) {
            final RichEditText et = tableEditText(pos, cp);
            if (et == null) continue;
            final int len = et.length();
            int from = cp == sChild ? sOff : 0;
            int to = cp == eChild ? eOff : len;
            if (sChild == eChild) { from = Math.min(sOff, eOff); to = Math.max(sOff, eOff); }
            from = Math.max(0, Math.min(from, len));
            to = Math.max(0, Math.min(to, len));
            if (from >= to) continue;
            if (add) {
                if (clearMask != 0) et.removeStyle(clearMask, from, to);
                et.addStyle(flag, from, to);
            } else {
                et.removeStyle(flag, from, to);
            }
            et.invalidateEffects();
            et.requestLayout();
            persistTableCell(pos, cp);
            changed = true;
        }
        suppressSpansChanged = false;
        if (changed && history != null) history.record();
        delegate.onSelectionChanged();
        if (changed) refreshSelectionHighlight();
    }

    boolean isStyleFullyAppliedTable(int flag, int pos, int sChild, int sOff, int eChild, int eOff) {
        boolean any = false;
        for (int cp = sChild; cp <= eChild; cp++) {
            final RichEditText et = tableEditText(pos, cp);
            if (et == null) continue;
            final int len = et.length();
            int from = cp == sChild ? sOff : 0;
            int to = cp == eChild ? eOff : len;
            if (sChild == eChild) { from = Math.min(sOff, eOff); to = Math.max(sOff, eOff); }
            from = Math.max(0, Math.min(from, len));
            to = Math.max(0, Math.min(to, len));
            if (from >= to) continue;
            any = true;
            if ((et.getCurrentStyle(from, to) & flag) == 0) return false;
        }
        return any;
    }

    private void onLinkClickedTable() {
        final int pos = textSelectionHelper.getStartCell();
        final int sChild = textSelectionHelper.getStartChildPosition();
        final int eChild = textSelectionHelper.getEndChildPosition();
        if (sChild != eChild) return;
        final RichEditText et = tableEditText(pos, sChild);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        if (RichTextStyle.hasLink(et.getText(), from, to)) {
            if (history != null) history.flush();
            RichTextStyle.removeLink(et.getText(), from, to);
            et.invalidateEffects();
            persistTableCell(pos, sChild);
            if (history != null) history.record();
            delegate.onSelectionChanged();
            refreshSelectionHighlight();
        } else {
            if (history != null) history.flush();
            if (RichTextStyle.hasDate(et.getText(), from, to)) {
                RichTextStyle.removeDate(et.getText(), from, to);
                et.invalidateEffects();
                persistTableCell(pos, sChild);
                if (history != null) history.record();
                refreshSelectionHighlight();
            }
            et.setSelectionOverride(from, to);
            et.makeSelectedUrl();
        }
    }

    private void onDateClickedTable() {
        final int pos = textSelectionHelper.getStartCell();
        final int sChild = textSelectionHelper.getStartChildPosition();
        final int eChild = textSelectionHelper.getEndChildPosition();
        if (sChild != eChild) return;
        final RichEditText et = tableEditText(pos, sChild);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        if (history != null) history.flush();
        if (RichTextStyle.hasLink(et.getText(), from, to)) {
            RichTextStyle.removeLink(et.getText(), from, to);
            et.invalidateEffects();
            persistTableCell(pos, sChild);
            if (history != null) history.record();
            refreshSelectionHighlight();
        }
        et.setSelectionOverride(from, to);
        et.makeSelectedDate();
    }

    boolean isCaptionSelection() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return false;
        final int s = textSelectionHelper.getStartCell();
        final int e = textSelectionHelper.getEndCell();
        if (s != e || s < 0 || s >= itemRows.size()) return false;
        return selectableAt(s) instanceof RichCaptionHost;
    }

    RichEditText captionEditText(int adapterPos) {
        final View v = selectableAt(adapterPos);
        return v instanceof RichCaptionHost ? ((RichCaptionHost) v).getCaptionEditText() : null;
    }

    boolean isQuoteAuthorSelection() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return false;
        final int s = textSelectionHelper.getStartCell();
        final int e = textSelectionHelper.getEndCell();
        if (s != e || s < 0 || s >= itemRows.size()) return false;
        if (textSelectionHelper.getStartChildPosition() != 1 || textSelectionHelper.getEndChildPosition() != 1) return false;
        final BlockRow row = rowForCell(s);
        return row != null && RichTextCell.isQuoteBlock(row.block);
    }

    RichEditText quoteAuthorEditText(int adapterPos) {
        final View v = selectableAt(adapterPos);
        return v instanceof RichTextCell ? ((RichTextCell) v).getAuthorEditText() : null;
    }

    private void persistQuoteAuthor(int adapterPos) {
        final View v = selectableAt(adapterPos);
        if (v instanceof RichTextCell) ((RichTextCell) v).persistAuthor();
    }

    private void persistCaption(int adapterPos) {
        final View v = selectableAt(adapterPos);
        if (v instanceof RichCaptionHost) ((RichCaptionHost) v).persistCaption();
    }

    private RichCaptionHost findCaptionHostAncestor(View v) {
        if (v instanceof RichCaptionHost) return (RichCaptionHost) v;
        android.view.ViewParent p = v.getParent();
        while (p != null) {
            if (p instanceof RichCaptionHost) return (RichCaptionHost) p;
            p = p.getParent();
        }
        return null;
    }

    private void onFormattingClickedCaption(int flag) {
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = captionEditText(pos);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        final boolean add = (et.getCurrentStyle(from, to) & flag) == 0;
        int clearMask = 0;
        if (add) {
            clearMask = clearMaskFor(flag);
        }
        if (history != null) history.flush();
        suppressSpansChanged = true;
        if (add) {
            if (clearMask != 0) et.removeStyle(clearMask, from, to);
            et.addStyle(flag, from, to);
        } else {
            et.removeStyle(flag, from, to);
        }
        et.invalidateEffects();
        et.requestLayout();
        persistCaption(pos);
        suppressSpansChanged = false;
        if (history != null) history.record();
        delegate.onSelectionChanged();
        refreshSelectionHighlight();
    }

    private void onLinkClickedCaption() {
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = captionEditText(pos);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        if (RichTextStyle.hasLink(et.getText(), from, to)) {
            if (history != null) history.flush();
            RichTextStyle.removeLink(et.getText(), from, to);
            et.invalidateEffects();
            persistCaption(pos);
            if (history != null) history.record();
            delegate.onSelectionChanged();
            refreshSelectionHighlight();
        } else {
            if (history != null) history.flush();
            if (RichTextStyle.hasDate(et.getText(), from, to)) {
                RichTextStyle.removeDate(et.getText(), from, to);
                et.invalidateEffects();
                persistCaption(pos);
                if (history != null) history.record();
                refreshSelectionHighlight();
            }
            et.setSelectionOverride(from, to);
            et.makeSelectedUrl();
        }
    }

    private void onDateClickedCaption() {
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = captionEditText(pos);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        if (history != null) history.flush();
        if (RichTextStyle.hasLink(et.getText(), from, to)) {
            RichTextStyle.removeLink(et.getText(), from, to);
            et.invalidateEffects();
            persistCaption(pos);
            if (history != null) history.record();
            refreshSelectionHighlight();
        }
        et.setSelectionOverride(from, to);
        et.makeSelectedDate();
    }

    private boolean captionSelectionHasFormattable() {
        final RichEditText et = captionEditText(textSelectionHelper.getStartCell());
        if (et == null) return false;
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final int from = Math.max(0, Math.min(Math.min(sOff, eOff), et.length()));
        final int to = Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        return from < to;
    }

    private void onFormattingClickedAuthor(int flag) {
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = quoteAuthorEditText(pos);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        final boolean add = (et.getCurrentStyle(from, to) & flag) == 0;
        int clearMask = 0;
        if (add) {
            clearMask = clearMaskFor(flag);
        }
        if (history != null) history.flush();
        suppressSpansChanged = true;
        if (add) {
            if (clearMask != 0) et.removeStyle(clearMask, from, to);
            et.addStyle(flag, from, to);
        } else {
            et.removeStyle(flag, from, to);
        }
        et.invalidateEffects();
        et.requestLayout();
        persistQuoteAuthor(pos);
        suppressSpansChanged = false;
        if (history != null) history.record();
        delegate.onSelectionChanged();
        refreshSelectionHighlight();
    }

    private void onLinkClickedAuthor() {
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = quoteAuthorEditText(pos);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        if (RichTextStyle.hasLink(et.getText(), from, to)) {
            if (history != null) history.flush();
            RichTextStyle.removeLink(et.getText(), from, to);
            et.invalidateEffects();
            persistQuoteAuthor(pos);
            if (history != null) history.record();
            delegate.onSelectionChanged();
            refreshSelectionHighlight();
        } else {
            if (history != null) history.flush();
            if (RichTextStyle.hasDate(et.getText(), from, to)) {
                RichTextStyle.removeDate(et.getText(), from, to);
                et.invalidateEffects();
                persistQuoteAuthor(pos);
                if (history != null) history.record();
                refreshSelectionHighlight();
            }
            et.setSelectionOverride(from, to);
            et.makeSelectedUrl();
        }
    }

    private void onDateClickedAuthor() {
        final int pos = textSelectionHelper.getStartCell();
        final RichEditText et = quoteAuthorEditText(pos);
        if (et == null) return;
        final int len = et.length();
        final int from = Math.max(0, Math.min(Math.min(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        final int to = Math.max(0, Math.min(Math.max(textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset()), len));
        if (from >= to) return;
        if (history != null) history.flush();
        if (RichTextStyle.hasLink(et.getText(), from, to)) {
            RichTextStyle.removeLink(et.getText(), from, to);
            et.invalidateEffects();
            persistQuoteAuthor(pos);
            if (history != null) history.record();
            refreshSelectionHighlight();
        }
        et.setSelectionOverride(from, to);
        et.makeSelectedDate();
    }

    private boolean quoteAuthorSelectionHasFormattable() {
        final RichEditText et = quoteAuthorEditText(textSelectionHelper.getStartCell());
        if (et == null) return false;
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        final int from = Math.max(0, Math.min(Math.min(sOff, eOff), et.length()));
        final int to = Math.max(0, Math.min(Math.max(sOff, eOff), et.length()));
        return from < to;
    }

    boolean selectionHasInlineFormattable() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return false;
        if (isTableSelection()) return tableSelectionHasFormattable();
        if (isCaptionSelection()) return captionSelectionHasFormattable();
        if (isQuoteAuthorSelection()) return quoteAuthorSelectionHasFormattable();
        final int sCell = textSelectionHelper.getStartCell();
        final int eCell = textSelectionHelper.getEndCell();
        final int sOff = textSelectionHelper.getStartOffset();
        final int eOff = textSelectionHelper.getEndOffset();
        if (sCell < 0 || eCell < 0 || eCell < sCell || eCell >= itemRows.size()) return false;
        for (int i = sCell; i <= eCell; i++) {
            final BlockRow r = rowForCell(i);
            if (r == null) continue;
            if (r.authorQuoteId == 0 && !isInlineFormattable(r.block)) continue;
            int len = blockTextLength(i);
            int from = i == sCell ? sOff : 0;
            int to = i == eCell ? eOff : len;
            from = Math.max(0, Math.min(from, len));
            to = Math.max(0, Math.min(to, len));
            if (from < to) return true;
        }
        return false;
    }

    static boolean isHeading(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockHeading1
            || b instanceof TL_iv.pageBlockHeading2
            || b instanceof TL_iv.pageBlockHeading3
            || b instanceof TL_iv.pageBlockHeading4
            || b instanceof TL_iv.pageBlockHeading5
            || b instanceof TL_iv.pageBlockHeading6;
    }

    boolean isSelectionAllHeadings() {
        if (textSelectionHelper == null || !textSelectionHelper.isInSelectionMode()) return false;
        final int sCell = textSelectionHelper.getStartCell();
        final int eCell = textSelectionHelper.getEndCell();
        if (sCell < 0 || eCell < 0 || eCell < sCell) return false;
        boolean any = false;
        for (int i = sCell; i <= eCell; i++) {
            final BlockRow row = rowForCell(i);
            if (row == null) continue;
            if (!isHeading(row.block)) return false;
            any = true;
        }
        return any;
    }

    private static boolean endsWithOwnParagraph(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockPreformatted
            || b instanceof TL_iv.pageBlockBlockquote
            || b instanceof TL_iv.pageBlockPullquote;
    }

    private static boolean demotesToParagraph(TL_iv.PageBlock b) {
        return isHeading(b)
            || b instanceof TL_iv.pageBlockPreformatted
            || b instanceof TL_iv.pageBlockBlockquote;
    }

    private int bottomInset;
    private int imeInset;
    private int emojiPadding;

    private boolean allowTapAboveContent = true;
    public void setAllowTapAboveContent(boolean allow) { allowTapAboveContent = allow; }

    public void setInsets(int bottomInset, int imeInset, int emojiPadding) {
        this.bottomInset = bottomInset;
        this.imeInset = imeInset;
        this.emojiPadding = emojiPadding;
    }

    public boolean handleSelectionTouch(MotionEvent ev) {
        switch (ev.getAction()) {
            case MotionEvent.ACTION_DOWN: {
                pressX = ev.getX();
                pressY = ev.getY();
                pressMoved = false;
                longPressConsumed = false;
                pressTarget = findCellUnder((int) pressX, (int) pressY);

                final long now = ev.getEventTime();
                final boolean doubleTap = !textSelectionHelper.isInSelectionMode()
                    && (now - lastTapDownTime) <= ViewConfiguration.getDoubleTapTimeout()
                    && Math.abs(pressX - lastTapDownX) <= dp(24)
                    && Math.abs(pressY - lastTapDownY) <= dp(24);
                lastTapDownTime = now;
                lastTapDownX = pressX;
                lastTapDownY = pressY;
                if (doubleTap && tryStartTextSelection(pressTarget, pressX, pressY)) {
                    longPressConsumed = true;
                    lastTapDownTime = 0;
                    textSelectionHelper.finishOneTouchSelection();
                    return true;
                }

                if (pressTarget != null) {
                    if (longPressRunnable != null) removeCallbacks(longPressRunnable);
                    longPressRunnable = () -> {
                        if (pressTarget == null) return;
                        if (textSelectionHelper.isInSelectionMode()) return;
                        if (tryStartTextSelection(pressTarget, pressX, pressY)) {
                            longPressConsumed = true;
                            return;
                        }
                        int localX = (int) (pressX - pressTarget.getLeft() - getLeft());
                        int localY = (int) (pressY - pressTarget.getTop() - getTop());
                        if (pressTarget instanceof RichTableCell) {
                            RichTableCell cell = (RichTableCell) pressTarget;
                            if (handleTableHandleTap(cell, localX, localY)) {
                                try { cell.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Exception ignore) {}
                                longPressConsumed = true;
                                return;
                            }
                            TL_iv.pageTableCell tableCell = cell.findCellAt(localX, localY);
                            if (tableCell != null) {
                                enterCellSelectionMode(cell, tableCell);
                                try { cell.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS); } catch (Exception ignore) {}
                                longPressConsumed = true;
                            } else {
                                startBlockDrag(pressTarget);
                            }
                        } else if (isPressOnEmptyEditText(pressTarget, localX, localY)) {
                            longPressConsumed = true;
                        } else {
                            startBlockDrag(pressTarget);
                        }
                    };
                    postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout());
                }
                break;
            }
            case MotionEvent.ACTION_MOVE: {
                float dx = ev.getX() - pressX;
                float dy = ev.getY() - pressY;
                if (dx * dx + dy * dy > dp(8) * dp(8)) {
                    pressMoved = true;
                    if (longPressRunnable != null) {
                        removeCallbacks(longPressRunnable);
                        longPressRunnable = null;
                    }
                }
                break;
            }
            case MotionEvent.ACTION_UP: {
                if (longPressRunnable != null) {
                    removeCallbacks(longPressRunnable);
                    longPressRunnable = null;
                }
                if (!pressMoved && !longPressConsumed && pressTarget instanceof RichTableCell) {
                    RichTableCell rtc = (RichTableCell) pressTarget;
                    int hLocalX = (int) (ev.getX() - rtc.getLeft() - getLeft());
                    int hLocalY = (int) (ev.getY() - rtc.getTop() - getTop());
                    if (handleTableHandleTap(rtc, hLocalX, hLocalY)) {
                        pressTarget = null;
                        longPressConsumed = false;
                        break;
                    }
                }
                if (!pressMoved && !longPressConsumed && activeCellSelectionTable != null) {
                    if (pressTarget == activeCellSelectionTable) {
                        RichTableCell rtc = (RichTableCell) pressTarget;
                        int localX = (int) (ev.getX() - rtc.getLeft() - getLeft());
                        int localY = (int) (ev.getY() - rtc.getTop() - getTop());
                        TL_iv.pageTableCell tCell = rtc.findCellAt(localX, localY);
                        if (tCell != null) {
                            if (isDotSelection()) {
                                exitCellSelectionMode();
                            } else {
                                rtc.toggleCellSelection(tCell);
                            }
                        }
                    } else if (pressTarget != null) {
                        exitCellSelectionMode();
                    }
                }
                if (!pressMoved && !longPressConsumed && pressTarget == null
                        && !textSelectionHelper.isInSelectionMode()
                        && activeCellSelectionTable == null
                        && isTapBelowContent(ev.getX(), ev.getY())) {
                    onTapBelowContent();
                }
                if (!pressMoved && !longPressConsumed && pressTarget == null
                        && !textSelectionHelper.isInSelectionMode()
                        && activeCellSelectionTable == null
                        && isTapAboveContent(ev.getX(), ev.getY())) {
                    onTapAboveContent();
                }
                if (!pressMoved && !longPressConsumed && pressTarget instanceof RichMathCell
                        && !textSelectionHelper.isInSelectionMode()
                        && activeCellSelectionTable == null) {
                    openMathEditor(((RichMathCell) pressTarget).getRow());
                }
                pressTarget = null;
                longPressConsumed = false;
                break;
            }
            case MotionEvent.ACTION_CANCEL: {
                if (longPressRunnable != null) {
                    removeCallbacks(longPressRunnable);
                    longPressRunnable = null;
                }
                pressTarget = null;
                longPressConsumed = false;
                break;
            }
        }
        return false;
    }

    private boolean tryStartTextSelection(View target, float px, float py) {
        if (target == null || textSelectionHelper.isInSelectionMode()) return false;
        int localX = (int) (px - target.getLeft() - getLeft());
        int localY = (int) (py - target.getTop() - getTop());
        if (target instanceof RichTextCell) {
            RichTextCell cell = (RichTextCell) target;
            if (cell.isPressOnText(localX, localY)) {
                textSelectionHelper.setMaybeView(localX, localY, cell);
                textSelectionHelper.trySelect(cell);
                return true;
            }
        } else if (target instanceof RichTableCell) {
            RichTableCell cell = (RichTableCell) target;
            if (cell.isPressOnText(localX, localY) || cell.isPressOnTitle(localX, localY)) {
                textSelectionHelper.setMaybeView(localX, localY, cell);
                textSelectionHelper.trySelect(cell);
                return true;
            }
        } else if (target instanceof RichCaptionHost) {
            if (((RichCaptionHost) target).isPressOnCaption(localX, localY)) {
                textSelectionHelper.setMaybeView(localX, localY, target);
                textSelectionHelper.trySelect(target);
                return true;
            }
        } else if (target instanceof RichDetailsCell) {
            RichDetailsCell cell = (RichDetailsCell) target;
            if (cell.isPressOnText(localX, localY)) {
                textSelectionHelper.setMaybeView(localX, localY, cell);
                textSelectionHelper.trySelect(cell);
                return true;
            }
        } else if (target instanceof RichMathCell) {
            if (((RichMathCell) target).isPressOnMath(localX, localY)) {
                textSelectionHelper.setMaybeView(localX, localY, target);
                textSelectionHelper.trySelect(target);
                return true;
            }
        }
        return false;
    }

    private View findCellUnder(int x, int y) {
        int listY = y - getTop();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (listY >= child.getTop() && listY < child.getBottom() && x >= child.getLeft() && x < child.getRight()) {
                return child;
            }
        }
        return null;
    }

    private boolean isTapBelowContent(float x, float y) {
        final View container = (View) getParent();
        if (container == null) return false;
        if (x < getLeft() || x > getRight()) return false;
        int contentBottom = getTop() + getPaddingTop();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            contentBottom = Math.max(contentBottom, getTop() + child.getBottom());
        }
        final int bottom = Math.max(Math.max(emojiPadding, bottomInset), imeInset);
        final int bottomLimit = container.getHeight() - dp(8 + 44 + 8) - bottom;
        return y >= contentBottom && y <= bottomLimit;
    }

    private boolean isTapAboveContent(float x, float y) {
        if (!allowTapAboveContent) return false;
        if (x < getLeft() || x > getRight()) return false;
        int contentTop = Integer.MAX_VALUE;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (getChildAdapterPosition(child) == 0) {
                contentTop = getTop() + child.getTop();
                break;
            }
        }
        if (contentTop == Integer.MAX_VALUE) return false;
        final int topLimit = getTop() + dp(8 + 44 + 8);
        return y >= topLimit && y <= contentTop;
    }

    private void onTapAboveContent() {
        final BlockRow first = rows.isEmpty() ? null : rows.get(0);
        if (first != null && !isNonText(first.block) && !first.detailsEnd && !isDetailsHeader(first) && !endsWithOwnParagraph(first.block)) {
            focusRowAtStart(first);
        } else {
            if (history != null) history.flush();
            final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
            rows.add(0, para);
            adapter.update(false);
            if (history != null) history.record();
            post(() -> focusRowAtStart(para));
        }
    }

    public void focusForDraft() {
        if (!focusForDraftInternal()) {
            post(this::focusForDraftInternal);
        }
    }

    private boolean focusForDraftInternal() {
        final BlockRow last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (last != null && !isNonText(last.block) && !last.detailsEnd && !isDetailsHeader(last) && !endsWithOwnParagraph(last.block)) {
            if (!(findViewByItemObject(last) instanceof RichTextCell)) return false;
            focusRowAtEnd(last);
        } else {
            if (history != null) history.flush();
            final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
            rows.add(para);
            adapter.update(false);
            if (history != null) history.record();
            post(() -> focusRowAtEnd(para));
        }
        return true;
    }

    private void onTapBelowContent() {
        final BlockRow last = rows.isEmpty() ? null : rows.get(rows.size() - 1);
        if (last != null && last.quoteIds.isEmpty() && !isNonText(last.block) && !last.detailsEnd
                && !isDetailsHeader(last) && !endsWithOwnParagraph(last.block)) {
            focusRowAtEnd(last);
        } else {
            if (history != null) history.flush();
            final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
            rows.add(para);
            adapter.update(false);
            if (history != null) history.record();
            post(() -> focusRowAtEnd(para));
        }
    }

    private BlockRow draggingRow;
    private boolean draggingOverTrash;

    @Override
    protected void onReorderStart(RecyclerView.ViewHolder viewHolder) {
        draggingRow = rowFromHolder(viewHolder);
        draggingOverTrash = false;
        if (delegate != null) delegate.onReorderStart();
    }

    @Override
    protected void onReorderMoved(RecyclerView.ViewHolder viewHolder) {
        if (delegate == null || viewHolder == null) return;
        final View v = viewHolder.itemView;
        final int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        final float cx = loc[0] + v.getWidth() / 2f;
        final float bottom = loc[1] + v.getHeight();
        draggingOverTrash = delegate.onReorderMove(cx, bottom);
    }

    @Override
    protected void onReorderEnd(RecyclerView.ViewHolder viewHolder) {
        if (delegate != null) delegate.onReorderEnd();
        resyncInsetCells(true);
    }

    @Override
    protected boolean isReorderRemoving() {
        return draggingOverTrash;
    }

    @Override
    protected void onReorderRemove(RecyclerView.ViewHolder viewHolder) {
        final BlockRow row = draggingRow;
        draggingRow = null;
        draggingOverTrash = false;
        if (row != null) {
            if (history != null) history.flush();
            removeRow(row);
            if (history != null) history.record();
            if (delegate != null) delegate.onContentChanged();
        } else if (viewHolder != null) {
            viewHolder.itemView.setTranslationX(0);
            viewHolder.itemView.setTranslationY(0);
        }
    }

    private BlockRow rowFromHolder(RecyclerView.ViewHolder holder) {
        if (holder == null) return null;
        final int pos = holder.getAdapterPosition();
        if (pos < 0) return null;
        final UItem item = adapter.getItem(pos);
        return item != null && item.object instanceof BlockRow ? (BlockRow) item.object : null;
    }

    private boolean isPressOnEmptyEditText(View target, int localX, int localY) {
        if (target instanceof RichTextCell) {
            return ((RichTextCell) target).isPressOnEmptyEditText(localX, localY);
        }
        if (target instanceof RichDetailsCell) {
            return ((RichDetailsCell) target).isPressOnEmptyEditText(localX, localY);
        }
        return false;
    }

    private void startBlockDrag(View cell) {
        if (cell == null || itemTouchHelper == null || !isReorderAllowed()) {
            return;
        }
        RecyclerView.ViewHolder holder = getChildViewHolder(cell);
        if (holder == null) return;
        int pos = holder.getAdapterPosition();
        if (pos < 0 || !adapter.isReorderItem(pos)) return;
        longPressConsumed = true;
        if (textSelectionHelper.isInSelectionMode()) textSelectionHelper.clear();
        try {
            cell.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);
        } catch (Exception ignore) {}
        itemTouchHelper.startDrag(holder);
    }

    private void setEditTextsLocked(boolean locked) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof RichTextCell) {
                ((RichTextCell) child).setLocked(locked);
            } else if (child instanceof RichTableCell) {
                ((RichTableCell) child).setLocked(locked);
            } else if (child instanceof RichCaptionHost) {
                ((RichCaptionHost) child).getCaptionEditText().setLocked(locked);
            } else if (child instanceof RichDetailsCell) {
                ((RichDetailsCell) child).setLocked(locked);
            } else if (child instanceof RichDividerCell) {
                child.invalidate();
            }
        }
    }

    private void hideEditTextActionModes() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof RichTextCell) {
                ((RichTextCell) child).hideActionModes();
            } else if (child instanceof RichTableCell) {
                ((RichTableCell) child).hideActionModes();
            } else if (child instanceof RichCaptionHost) {
                ((RichCaptionHost) child).getCaptionEditText().hideActionMode();
            } else if (child instanceof RichDetailsCell) {
                ((RichDetailsCell) child).getEditText().hideActionMode();
            }
        }
    }

    private void finishEditTextActionModes() {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof RichTextCell) {
                ((RichTextCell) child).finishActionModes();
            } else if (child instanceof RichCaptionHost) {
                ((RichCaptionHost) child).getCaptionEditText().finishActionMode();
            } else if (child instanceof RichDetailsCell) {
                ((RichDetailsCell) child).getEditText().finishActionMode();
            }
        }
    }

    public boolean deselectIfAny() {
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            textSelectionHelper.clear();
            return true;
        }
        if (activeCellSelectionTable != null) {
            exitCellSelectionMode();
            return true;
        }
        return false;
    }

    private void restoreFocusAt(int adapterPos, int childPos, int offset) {
        if (adapterPos < 0) return;
        View v = layoutManager.findViewByPosition(adapterPos);
        if (v instanceof RichTextCell) {
            RichTextCell c = (RichTextCell) v;
            final RichEditText et = childPos == 1 && c.isAuthorVisible() ? c.getAuthorEditText() : c.getEditText();
            et.requestEditFocusRebuild();
            int len = et.length();
            et.setSelection(Math.max(0, Math.min(offset, len)));
        } else if (v instanceof RichTableCell) {
            RichTableCell rtc = (RichTableCell) v;
            RichEditText et = rtc.editTextForChildPos(childPos);
            if (et == null) et = rtc.editTextForChildPos(rtc.titleChildPos());
            if (et == null) return;
            et.requestEditFocusRebuild();
            et.setSelection(Math.max(0, Math.min(offset, et.length())));
        } else if (v instanceof RichCaptionHost) {
            RichEditText et = ((RichCaptionHost) v).getCaptionEditText();
            et.requestEditFocusRebuild();
            int len = et.length();
            et.setSelection(Math.max(0, Math.min(offset, len)));
        }
    }

    private boolean restoreFocusAtScreenPoint(float rawX, float rawY) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) child;
                if (placeCaretAtScreenPoint(c.getEditText(), rawX, rawY)) return true;
                if (c.isAuthorVisible() && placeCaretAtScreenPoint(c.getAuthorEditText(), rawX, rawY)) return true;
            } else if (child instanceof RichCaptionHost) {
                if (placeCaretAtScreenPoint(((RichCaptionHost) child).getCaptionEditText(), rawX, rawY)) return true;
            } else if (child instanceof RichDetailsCell) {
                if (placeCaretAtScreenPoint(((RichDetailsCell) child).getEditText(), rawX, rawY)) return true;
            }
        }
        return false;
    }

    private boolean placeCaretAtScreenPoint(RichEditText et, float rawX, float rawY) {
        if (et == null || et.getVisibility() != VISIBLE) return false;
        final int[] loc = new int[2];
        et.getLocationOnScreen(loc);
        final float lx = rawX - loc[0];
        final float ly = rawY - loc[1];
        if (lx < 0 || ly < 0 || lx > et.getWidth() || ly > et.getHeight()) return false;
        int off = et.getOffsetForPosition(lx, ly);
        if (off < 0) off = 0;
        et.requestEditFocusRebuild();
        et.setSelection(Math.max(0, Math.min(off, et.length())));
        return true;
    }

    public TextSelectionHelper.ArticleTextSelectionHelper getTextSelectionHelper() {
        return textSelectionHelper;
    }

    private static final int DEFAULT_ATTACH_LAYOUTS =
        (1 << ChatAttachAlert.LAYOUT_TYPE_PHOTO) |
        (1 << ChatAttachAlert.LAYOUT_TYPE_MUSIC) |
        (1 << ChatAttachAlert.LAYOUT_TYPE_LOCATION);

    private final RichDividerCell.Delegate dividerDelegate = this::getTextSelectionHelper;

    private final RichMediaCell.Delegate mediaDelegate = new RichMediaCell.Delegate() {
        @Override public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) { delegate.makeEditTextFocusable(et, showKeyboard); }
        @Override public void onMediaPick(BlockRow row) { pendingMediaRow = row; delegate.onOpenAttachRequest(DEFAULT_ATTACH_LAYOUTS, 0); }
        @Override public void onAddMedia(BlockRow row) { pendingMediaRow = row; delegate.onOpenAttachRequest(DEFAULT_ATTACH_LAYOUTS, 0); }
        @Override public void onSwitchMode(BlockRow row) { switchGalleryMode(row); }
        @Override public void onCancelUpload(BlockRow row, MediaUploadState media) { cancelMediaUpload(row, media); }
        @Override public void onDeleteMedia(BlockRow row, MediaUploadState media) { cancelMediaUpload(row, media); }
        @Override public void onToggleSpoiler(BlockRow row, MediaUploadState media) { toggleMediaSpoiler(row, media); }
        @Override public ItemOptions makeMenu(View anchor) { return delegate.makeMenu(anchor); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
        @Override public void onCaptionWillChange(BlockRow row, int removed, int added) { if (history != null) history.onBeforeChange(removed, added); }
        @Override public void onCaptionChanged(BlockRow row) { if (history != null) history.onTyping(); delegate.onContentChanged(); }
        @Override public void onCaptionSpansChanged(BlockRow row) { onCellSpansChanged(); }
        @Override public void onCaptionEnter(BlockRow row) { RichEditorListView.this.onCaptionEnter(row); }
        @Override public void onCaptionLockedInsert(CharSequence text) { if (text != null && text.length() > 0) replaceHelperSelectionWith(text.toString()); }
        @Override public boolean onCaptionSelectAll(BlockRow row) { return tryEscalateSelectAll(); }
    };

    private void toggleMediaSpoiler(BlockRow row, MediaUploadState media) {
        if (row == null || media == null) return;
        if (history != null) history.flush();
        media.hasSpoiler = !media.hasSpoiler;
        final TL_iv.PageBlock item = itemBlockFor(row, media);
        if (item instanceof TL_iv.pageBlockPhoto) {
            ((TL_iv.pageBlockPhoto) item).spoiler = media.hasSpoiler;
        } else if (item instanceof TL_iv.pageBlockVideo) {
            ((TL_iv.pageBlockVideo) item).spoiler = media.hasSpoiler;
        }
        refreshMediaCell(row);
        if (history != null) history.record();
        delegate.onContentChanged();
    }

    private final RichAudioCell.Delegate audioDelegate = new RichAudioCell.Delegate() {
        @Override public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) { delegate.makeEditTextFocusable(et, showKeyboard); }
        @Override public void onCancelUpload(BlockRow row) { cancelAudioUpload(row); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
        @Override public void onCaptionWillChange(BlockRow row, int removed, int added) { if (history != null) history.onBeforeChange(removed, added); }
        @Override public void onCaptionChanged(BlockRow row) { if (history != null) history.onTyping(); delegate.onContentChanged(); }
        @Override public void onCaptionSpansChanged(BlockRow row) { onCellSpansChanged(); }
        @Override public void onCaptionEnter(BlockRow row) { RichEditorListView.this.onCaptionEnter(row); }
        @Override public void onCaptionLockedInsert(CharSequence text) { if (text != null && text.length() > 0) replaceHelperSelectionWith(text.toString()); }
        @Override public boolean onCaptionSelectAll(BlockRow row) { return tryEscalateSelectAll(); }
    };

    private final IdentityHashMap<MediaUploadState, RichMediaUploader> uploaders = new IdentityHashMap<>();
    private final IdentityHashMap<MediaUploadState, RichMediaConverter> converters = new IdentityHashMap<>();
    BlockRow pendingMediaRow;
    BlockRow pendingInsertRow;

    private final RichMapCell.Delegate mapDelegate = new RichMapCell.Delegate() {
        @Override public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) { delegate.makeEditTextFocusable(et, showKeyboard); }
        @Override public void onPickLocation(BlockRow row) { delegate.onOpenLocationRequest(row); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
        @Override public void onCaptionWillChange(BlockRow row, int removed, int added) { if (history != null) history.onBeforeChange(removed, added); }
        @Override public void onCaptionChanged(BlockRow row) { if (history != null) history.onTyping(); delegate.onContentChanged(); }
        @Override public void onCaptionSpansChanged(BlockRow row) { onCellSpansChanged(); }
        @Override public void onCaptionEnter(BlockRow row) { RichEditorListView.this.onCaptionEnter(row); }
        @Override public void onCaptionLockedInsert(CharSequence text) { if (text != null && text.length() > 0) replaceHelperSelectionWith(text.toString()); }
        @Override public boolean onCaptionSelectAll(BlockRow row) { return tryEscalateSelectAll(); }
    };

    RichMapCell.Delegate getMapDelegate() { return mapDelegate; }

    private final RichMathCell.Delegate mathDelegate = new RichMathCell.Delegate() {
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
    };

    private final RichQuoteAuthorCell.Delegate quoteAuthorDelegate = new RichQuoteAuthorCell.Delegate() {
        @Override public TL_iv.RichText getQuoteAuthor(long qid) { return quoteAuthors.get(qid); }
        @Override public void setQuoteAuthor(long qid, TL_iv.RichText text) {
            if (text == null || text instanceof TL_iv.textEmpty) quoteAuthors.remove(qid);
            else quoteAuthors.put(qid, text);
        }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
        @Override public void onQuoteAuthorEnter(BlockRow authorRow) { insertParagraphAfterQuote(authorRow); }
    };

    private void insertParagraphAfterQuote(BlockRow authorRow) {
        final long qid = authorRow.authorQuoteId;
        if (qid == 0) return;
        int lastIdx = -1;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).quoteIds.contains(qid)) lastIdx = i;
        }
        if (lastIdx < 0) return;
        if (history != null) history.flush();
        final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
        para.quoteIds.addAll(authorRow.quoteIds);
        if (!para.quoteIds.isEmpty()) para.quoteIds.remove(para.quoteIds.size() - 1);
        rows.add(lastIdx + 1, para);
        renumberAllRuns();
        adapter.update(false);
        if (history != null) history.record();
        post(() -> focusRow(para));
    }

    private void openMathEditor(BlockRow row) {
        if (row == null || !(row.block instanceof TL_iv.pageBlockMath)) return;
        final TL_iv.pageBlockMath math = (TL_iv.pageBlockMath) row.block;
        ChatAttachAlertRichLayout.showEditLatexSheet(getContext(), TextUtils.isEmpty(math.source) ? "" : math.source, source -> {
            if (TextUtils.equals(source, math.source)) return;
            if (history != null) history.flush();
            math.source = source;
            adapter.update(false);
            if (history != null) history.record();
            delegate.onContentChanged();
        }, resourcesProvider);
    }

    private final RichTableCell.Delegate tableDelegate = new RichTableCell.Delegate() {
        @Override public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) { delegate.makeEditTextFocusable(et, showKeyboard); }
        @Override public void onTextChanged(BlockRow row) { if (history != null) history.onTyping(); delegate.onContentChanged(); }
        @Override public void onTextWillChange(BlockRow row, int removed, int added) { if (history != null) history.onBeforeChange(removed, added); }
        @Override public void onSpansChanged(BlockRow row) { onCellSpansChanged(); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
        @Override public void onLockedInsert(CharSequence text) { if (text != null && text.length() > 0) replaceHelperSelectionWith(text.toString()); }
        @Override public boolean onSelectAll(BlockRow row) { return tryEscalateSelectAll(); }
    };

    private final RichDetailsCell.Delegate detailsDelegate = new RichDetailsCell.Delegate() {
        @Override public void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) { delegate.makeEditTextFocusable(editText, showKeyboard); }
        @Override public void onToggle(BlockRow row) { toggleDetails(row); }
        @Override public void onTitleChanged(BlockRow row) { if (history != null) history.onTyping(); delegate.onContentChanged(); }
        @Override public void onTitleEnter(BlockRow row) { onDetailsTitleEnter(row); }
        @Override public void onTitleBackspace(BlockRow row) { deleteDetails(row); }
        @Override public void onSpansChanged(BlockRow row) { onCellSpansChanged(); }
        @Override public void onLockedInsert(CharSequence text) { if (text != null && text.length() > 0) replaceHelperSelectionWith(text.toString()); }
        @Override public boolean onSelectAll(BlockRow row) { return tryEscalateSelectAll(); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
    };

    private void deleteDetails(BlockRow header) {
        final int idx = rows.indexOf(header);
        if (idx < 0 || !isDetailsHeader(header)) return;
        final int end = matchingDetailsEnd(idx);
        final int endIncl = end >= rows.size() ? rows.size() - 1 : end;
        if (history != null) history.flush();
        for (int i = endIncl; i >= idx; i--) rows.remove(i);
        BlockRow focusTarget = idx > 0 ? rows.get(idx - 1) : null;
        if (focusTarget == null || focusTarget.detailsEnd || isDetailsHeader(focusTarget) || isNonText(focusTarget.block)) {
            focusTarget = null;
        }
        if (rows.isEmpty()) {
            final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
            rows.add(para);
            focusTarget = para;
        }
        adapter.update(false);
        if (history != null) history.record();
        final BlockRow target = focusTarget;
        if (target != null) post(() -> focusRowAtEnd(target));
    }

    private void toggleDetails(BlockRow row) {
        if (!isDetailsHeader(row)) return;
        final TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) row.block;
        if (history != null) history.flush();
        details.open = !details.open;
        adapter.update(true);
        if (history != null) history.record();
    }

    private void onDetailsTitleEnter(BlockRow row) {
        final int idx = rows.indexOf(row);
        if (idx < 0) return;
        final TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) row.block;
        if (!details.open) {
            details.open = true;
            adapter.update(true);
        }
        if (idx + 1 < rows.size() && !rows.get(idx + 1).detailsEnd && !isDetailsHeader(rows.get(idx + 1))) {
            final BlockRow target = rows.get(idx + 1);
            post(() -> focusRow(target));
        }
    }

    RichTableCell activeCellSelectionTable;
    private ItemOptions tableCellMenu;
    private int dotSelectedRow = -1, dotSelectedCol = -1;
    private final RichTableCell.CellSelectionListener cellSelectionListener = table -> {
        if (table != activeCellSelectionTable) return;
        if (!table.hasCellSelection()) {
            exitCellSelectionMode();
        } else {
            showTableCellMenu(table);
        }
    };

    private void beginCellSelection(RichTableCell table) {
        if (activeCellSelectionTable != null && activeCellSelectionTable != table) {
            activeCellSelectionTable.clearCellSelection();
        }
        activeCellSelectionTable = table;
        dotSelectedRow = dotSelectedCol = -1;
        table.setCellSelectionListener(cellSelectionListener);
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            textSelectionHelper.clear();
        }
        setEditTextsLocked(true);
    }

    private boolean isDotSelection() {
        return dotSelectedRow >= 0 || dotSelectedCol >= 0;
    }

    void enterCellSelectionMode(RichTableCell table, TL_iv.pageTableCell cell) {
        beginCellSelection(table);
        table.addCellToSelection(cell);
    }

    private boolean handleTableHandleTap(RichTableCell table, int localX, int localY) {
        final int row = table.findRowHandleAt(localX, localY);
        if (row >= 0) {
            if (table == activeCellSelectionTable && dotSelectedRow == row) {
                exitCellSelectionMode();
            } else {
                beginCellSelection(table);
                table.selectWholeRow(row);
                dotSelectedRow = row;
            }
            return true;
        }
        final int col = table.findColHandleAt(localX, localY);
        if (col >= 0) {
            if (table == activeCellSelectionTable && dotSelectedCol == col) {
                exitCellSelectionMode();
            } else {
                beginCellSelection(table);
                table.selectWholeColumn(col);
                dotSelectedCol = col;
            }
            return true;
        }
        return false;
    }

    private void exitCellSelectionMode() {
        dismissTableCellMenu();
        if (activeCellSelectionTable != null) {
            activeCellSelectionTable.clearCellSelection();
            activeCellSelectionTable = null;
        }
        dotSelectedRow = dotSelectedCol = -1;
        setEditTextsLocked(false);
    }

    private void dismissTableCellMenu() {
        if (tableCellMenu != null) {
            final ItemOptions menu = tableCellMenu;
            tableCellMenu = null;
            menu.dismiss();
        }
    }

    RichTableCell findFocusedTableCell() {
        final View f = findFocus();
        return f instanceof RichEditText ? findTableCellAncestor(f) : null;
    }

    TL_iv.pageTableCell focusedCellOf(RichTableCell table) {
        final View f = findFocus();
        if (!(f instanceof RichEditText)) return null;
        final RichTableCellHost host = table.findHostContaining(f);
        return host != null ? host.cell : null;
    }

    private View tableMenuAnchor(RichTableCell table, boolean bottom) {
        final TableModel m = table.getModel();
        if (m == null) return table;
        TL_iv.pageTableCell best = null;
        int bestR = bottom ? -1 : Integer.MAX_VALUE, bestC = Integer.MAX_VALUE;
        for (TL_iv.pageTableCell c : table.getSelectedCells()) {
            final int col = m.anchorColOf(c);
            final int edge = bottom ? m.anchorRowOf(c) + TableModel.spanRow(c) - 1 : m.anchorRowOf(c);
            final boolean better = bottom ? (edge > bestR || (edge == bestR && col < bestC))
                                          : (edge < bestR || (edge == bestR && col < bestC));
            if (better) {
                bestR = edge; bestC = col; best = c;
            }
        }
        if (best == null) return table;
        final RichTableCellHost host = table.getGrid().hostForAnchor(best);
        return host != null ? host : table;
    }

    void showTableCellMenu(RichTableCell table) {
        final TableModel m = table.getModel();
        if (m == null) return;
        final Set<TL_iv.pageTableCell> sel = table.getSelectedCells();
        if (sel.isEmpty()) return;
        final int n = sel.size();
        final boolean canMerge = n >= 2 && computeCanMerge(m, sel);
        final boolean canUnmerge = n == 1 && computeHasSpan(sel.iterator().next());
        final boolean fullRows = computeSpansFullRows(m, sel);
        final boolean fullCols = computeSpansFullColumns(m, sel);
        final boolean allSelected = computeAllSelected(m, sel);
        final boolean canDeleteRows = fullRows && !allSelected && distinctSelectedRows(m, sel) < m.rowCount;
        final boolean canDeleteCols = fullCols && !allSelected && distinctSelectedCols(m, sel) < m.colCount;
        final boolean canInsertCols = fullCols && m.colCount < MessagesController.getInstance(currentAccount).config.richMessageMaxTableCols.get();

        dismissTableCellMenu();

        final int itemCount = 1
            + (canMerge ? 1 : 0) + (canUnmerge ? 1 : 0)
            + (canInsertCols ? 2 : 0) + (fullRows ? 2 : 0)
            + (canDeleteCols ? 1 : 0) + (canDeleteRows ? 1 : 0)
            + (allSelected ? 1 : 0);
        final int estMenuHeight = dp(4 + 12 + 32 + 4 + 8 + itemCount * 48 + 8);
        final View topAnchor = tableMenuAnchor(table, false);
        final int[] anchorLoc = new int[2];
        topAnchor.getLocationOnScreen(anchorLoc);
        final boolean roomAbove = anchorLoc[1] - estMenuHeight - dp(8) >= AndroidUtilities.statusBarHeight + dp(8);

        final View anchor = roomAbove ? topAnchor : tableMenuAnchor(table, true);
        final ItemOptions o = delegate.makeMenu(anchor);
        o.setDimAlpha(0);
        o.setDrawScrim(false);
        o.allowShowingOnTopOfKeyboard();

        LinearLayout alignContainer = new LinearLayout(getContext());
        alignContainer.setOrientation(LinearLayout.VERTICAL);
        TextView alignTitle = new TextView(getContext());
        alignTitle.setText(getString(R.string.ArticleAlignment));
        alignTitle.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        alignTitle.setGravity(Gravity.CENTER);
        alignTitle.setTextColor(getThemedColor(Theme.key_windowBackgroundWhiteGrayText));
        alignContainer.addView(alignTitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 5, 0, 4));
        LinearLayout alignLayout = new LinearLayout(getContext());
        alignLayout.setPadding(dp(4), dp(4), dp(4), dp(4));
        alignLayout.setOrientation(LinearLayout.HORIZONTAL);
        alignContainer.addView(alignLayout, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER));

        final RichEditor.Button[] horiz = new RichEditor.Button[3];
        final RichEditor.Button[] vert = new RichEditor.Button[3];
        alignLayout.addView(
            horiz[0] = new RichEditor.Button(getContext(), R.drawable.iv_align_horiz_left, resourcesProvider).setRoundRadius(4).setAccent(false),
            LayoutHelper.createLinear(32, 32)
        );
        alignLayout.addView(
            horiz[1] = new RichEditor.Button(getContext(), R.drawable.iv_align_horiz_middle, resourcesProvider).setRoundRadius(4).setAccent(false),
            LayoutHelper.createLinear(32, 32)
        );
        alignLayout.addView(
            horiz[2] = new RichEditor.Button(getContext(), R.drawable.iv_align_horiz_right, resourcesProvider).setRoundRadius(4).setAccent(false),
            LayoutHelper.createLinear(32, 32)
        );
        alignLayout.addView(new Space(getContext()), LayoutHelper.createLinear(8, 0));
        alignLayout.addView(
            vert[0] = new RichEditor.Button(getContext(), R.drawable.iv_align_vert_top, resourcesProvider).setRoundRadius(4).setAccent(false),
            LayoutHelper.createLinear(32, 32)
        );
        alignLayout.addView(
            vert[1] = new RichEditor.Button(getContext(), R.drawable.iv_align_vert_middle, resourcesProvider).setRoundRadius(4).setAccent(false),
            LayoutHelper.createLinear(32, 32)
        );
        alignLayout.addView(
            vert[2] = new RichEditor.Button(getContext(), R.drawable.iv_align_vert_bottom, resourcesProvider).setRoundRadius(4).setAccent(false),
            LayoutHelper.createLinear(32, 32)
        );
        o.addView(alignContainer);

        final int curAlign = table.commonHorizontalAlign();
        final int curVAlign = table.commonVerticalAlign();
        for (int i = 0; i < 3; i++) horiz[i].setSelected(i == curAlign);
        for (int i = 0; i < 3; i++) vert[i].setSelected(i == curVAlign);

        for (int i = 0; i < 3; i++) {
            final int align = i;
            horiz[i].setOnClickListener(v -> {
                table.applyHorizontalAlign(align);
                for (int j = 0; j < 3; j++) horiz[j].setSelected(j == align);
            });
        }
        for (int i = 0; i < 3; i++) {
            final int valign = i;
            vert[i].setOnClickListener(v -> {
                table.applyVerticalAlign(valign);
                for (int j = 0; j < 3; j++) vert[j].setSelected(j == valign);
            });
        }
        o.addSpaceGap();

        final boolean highlighted = table.allSelectedHeader();
        final String highlightLabel;
        if (highlighted) {
            highlightLabel = getString(R.string.ArticleRemoveHighlight);
        } else {
            highlightLabel = fullCols ? getString(R.string.ArticleHighlightColumn) : (fullRows ? getString(R.string.ArticleHighlightRow) : getString(R.string.ArticleHighlightCell));
        }
        o.add(highlighted ? R.drawable.iv_table_highlight_remove : R.drawable.iv_table_highlight, highlightLabel, () -> {
            table.applyHeaderToggle(!highlighted);
            exitCellSelectionMode();
        });

        if (canMerge) {
            o.add(R.drawable.iv_table_merge, getString(R.string.ArticleMergeCells), () -> {
                table.applyMergeFromSelection();
                exitCellSelectionMode();
            });
        }
        if (canUnmerge) {
            o.add(R.drawable.iv_table_unmerge, getString(R.string.ArticleSplitCells), () -> {
                table.applyUnmergeFromSelection();
                exitCellSelectionMode();
            });
        }
        if (canInsertCols) {
            o.add(R.drawable.iv_table_insert_left, getString(R.string.ArticleInsertLeft), () -> {
                table.applyInsertColumnFromSelection(true);
                exitCellSelectionMode();
            });
            o.add(R.drawable.iv_table_insert_right, getString(R.string.ArticleInsertRight), () -> {
                table.applyInsertColumnFromSelection(false);
                exitCellSelectionMode();
            });
        }
        if (fullRows) {
            o.add(R.drawable.iv_table_insert_top, getString(R.string.ArticleInsertAbove), () -> {
                table.applyInsertRowFromSelection(true);
                exitCellSelectionMode();
            });
            o.add(R.drawable.iv_table_insert_bottom, getString(R.string.ArticleInsertBelow), () -> {
                table.applyInsertRowFromSelection(false);
                exitCellSelectionMode();
            });
        }
        if (canDeleteCols) {
            o.add(R.drawable.iv_table_remove, getString(R.string.ArticleDeleteColumn), true, () -> {
                table.applyDeleteColumnsFromSelection();
                exitCellSelectionMode();
            });
        }
        if (canDeleteRows) {
            o.add(R.drawable.iv_table_remove, getString(R.string.ArticleDeleteRow), true, () -> {
                table.applyDeleteRowsFromSelection();
                exitCellSelectionMode();
            });
        }
        if (allSelected) {
            o.add(R.drawable.iv_table_remove, getString(R.string.ArticleDeleteTable), true, () -> {
                final BlockRow row = table.getRow();
                exitCellSelectionMode();
                if (row != null) {
                    if (history != null) history.flush();
                    removeRow(row);
                    if (history != null) history.record();
                    if (delegate != null) delegate.onContentChanged();
                }
            });
        }
        o.setOnDismiss(() -> { if (tableCellMenu == o) tableCellMenu = null; });
        tableCellMenu = o;
        o.show();
    }

    private static boolean computeHasSpan(TL_iv.pageTableCell c) {
        return TableModel.spanCol(c) > 1 || TableModel.spanRow(c) > 1;
    }

    private static boolean computeCanMerge(TableModel m, Set<TL_iv.pageTableCell> sel) {
        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE, maxR = -1, maxC = -1;
        for (TL_iv.pageTableCell c : sel) {
            int ar = m.anchorRowOf(c), ac = m.anchorColOf(c);
            int rs = TableModel.spanRow(c), cs = TableModel.spanCol(c);
            minR = Math.min(minR, ar); minC = Math.min(minC, ac);
            maxR = Math.max(maxR, ar + rs - 1); maxC = Math.max(maxC, ac + cs - 1);
        }
        HashSet<TL_iv.pageTableCell> covered = new HashSet<>();
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                if (r < 0 || c < 0 || r >= m.rowCount || c >= m.colCount) return false;
                covered.add(m.grid[r][c]);
            }
        }
        return covered.equals(new HashSet<>(sel));
    }

    private static boolean computeSpansFullRows(TableModel m, Set<TL_iv.pageTableCell> sel) {
        HashSet<Integer> rowsHit = new HashSet<>();
        for (TL_iv.pageTableCell c : sel) rowsHit.add(m.anchorRowOf(c));
        if (rowsHit.isEmpty()) return false;
        for (int r : rowsHit) {
            if (r < 0 || r >= m.rowCount) return false;
            for (int c = 0; c < m.colCount; c++) {
                if (m.anchorR[r][c] != r) return false;
                if (!sel.contains(m.grid[r][c])) return false;
            }
        }
        return true;
    }

    private static boolean computeSpansFullColumns(TableModel m, Set<TL_iv.pageTableCell> sel) {
        final HashSet<Integer> colsHit = new HashSet<>();
        for (TL_iv.pageTableCell c : sel) colsHit.add(m.anchorColOf(c));
        if (colsHit.isEmpty()) return false;
        for (int c : colsHit) {
            if (c < 0 || c >= m.colCount) return false;
            for (int r = 0; r < m.rowCount; r++) {
                if (m.anchorC[r][c] != c) return false;
                if (!sel.contains(m.grid[r][c])) return false;
            }
        }
        return true;
    }

    private static boolean computeAllSelected(TableModel m, Set<TL_iv.pageTableCell> sel) {
        if (sel.isEmpty() || m.rowCount <= 0 || m.colCount <= 0) return false;
        final HashSet<TL_iv.pageTableCell> all = new HashSet<>();
        for (int r = 0; r < m.rowCount; r++) {
            for (int c = 0; c < m.colCount; c++) {
                all.add(m.grid[r][c]);
            }
        }
        return all.equals(new HashSet<>(sel));
    }

    private static int distinctSelectedRows(TableModel m, Set<TL_iv.pageTableCell> sel) {
        final HashSet<Integer> rows = new HashSet<>();
        for (TL_iv.pageTableCell c : sel) rows.add(m.anchorRowOf(c));
        return rows.size();
    }

    private static int distinctSelectedCols(TableModel m, Set<TL_iv.pageTableCell> sel) {
        final HashSet<Integer> cols = new HashSet<>();
        for (TL_iv.pageTableCell c : sel) cols.add(m.anchorColOf(c));
        return cols.size();
    }

    private final RichTextCell.Delegate cellDelegate = new RichTextCell.Delegate() {
        @Override public void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) { delegate.makeEditTextFocusable(editText, showKeyboard); }
        @Override public void onEnter(BlockRow row) { onCellEnter(row); }
        @Override public void onQuoteAuthorEnter(BlockRow row) { onCaptionEnter(row); }
        @Override public void onBackspace(BlockRow row) { onCellBackspaceAtStart(row, true); }
        @Override public boolean onBackspaceAtStart(BlockRow row) { return onCellBackspaceAtStart(row, false); }
        @Override public void onTextChanged(BlockRow row) { if (history != null) history.onTyping(); delegate.onContentChanged(); }
        @Override public void onTextWillChange(BlockRow row, int removed, int added) { if (history != null) history.onBeforeChange(removed, added); }
        @Override public void onTransform(BlockRow row, TL_iv.PageBlock newBlock, int newLevel, int newNum, boolean checkbox, boolean checked) {
            if (newBlock instanceof TL_iv.pageBlockBlockquote) { applyQuote(row); return; }
            transformRow(row, newBlock, newLevel, newNum, checkbox, checked);
        }
        @Override public void onCheckboxToggle(BlockRow row, boolean checked) { onChecklistToggle(row, checked); }
        @Override public void onSpansChanged(BlockRow row) { onCellSpansChanged(); }
        @Override public TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper() { return getTextSelectionHelper(); }
        @Override public boolean onIndent(BlockRow row, boolean outdent) { return onCellIndent(row, outdent); }
        @Override public void onLockedInsert(CharSequence text) { if (text != null && text.length() > 0) replaceHelperSelectionWith(text.toString()); }
        @Override public void onLanguageClick(BlockRow row, View button) { RichEditorListView.this.onLanguageClick(row, button); }
        @Override public boolean onSelectAll(BlockRow row) { return tryEscalateSelectAll(); }
        @Override public boolean onPaste(BlockRow row, RichEditText editText) { return onCellPaste(row, editText); }
        @Override public void onCommand(BlockRow row, int command) { handleSlashCommand(row, command); }
        @Override public void onSlashSuggest(RichTextCell cell, String query) {
            delegate.onSlashSuggest(cell, query);
        }
    };

    void handleSlashCommand(BlockRow row, int command) {
        pendingMediaRow = null;
        pendingInsertRow = row;
        if (history != null) history.flush();
        clearRowText(row);
        if (history != null) history.record();
        switch (command) {
            case RichTextCell.CMD_ATTACH_PHOTO:
            case RichTextCell.CMD_ATTACH_VIDEO:
                delegate.onOpenAttachRequest(DEFAULT_ATTACH_LAYOUTS, ChatAttachAlert.LAYOUT_TYPE_PHOTO);
                break;
            case RichTextCell.CMD_ATTACH_MUSIC:
                delegate.onOpenAttachRequest(DEFAULT_ATTACH_LAYOUTS, ChatAttachAlert.LAYOUT_TYPE_MUSIC);
                break;
            case RichTextCell.CMD_ATTACH_LOCATION:
                delegate.onOpenAttachRequest(DEFAULT_ATTACH_LAYOUTS, ChatAttachAlert.LAYOUT_TYPE_LOCATION);
                break;
            case RichTextCell.CMD_MATH:
                openLatexEditorAndAdd();
                break;
            case RichTextCell.CMD_DETAILS:
                insertDetails();
                break;
        }
    }

    private void clearRowText(BlockRow row) {
        if (row == null) return;
        RichTextCell.applyTextToBlock(row.block, "");
        View v = findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            ((RichTextCell) v).getEditText().setTextSilently("");
        }
    }

    private void openLatexEditorAndAdd() {
        ChatAttachAlertRichLayout.showEditLatexSheet(getContext(), "", source -> {
            if (TextUtils.isEmpty(source)) return;
            final TL_iv.pageBlockMath newMath = new TL_iv.pageBlockMath();
            newMath.source = source;
            addBlock(newMath);
        }, resourcesProvider);
    }

    private final RichEditorHistory.Delegate historyDelegate = new RichEditorHistory.Delegate() {
        @Override public ArrayList<BlockRow> getRows() { return rows; }
        @Override public void restoreRows(List<BlockRow> newRows, RichEditorHistory.FocusState focus) { restoreFromHistory(newRows, focus); }
        @Override public void onHistoryChanged() { delegate.onHistoryChanged(); }
        @Override public RichEditorHistory.FocusState captureFocus() { return captureFocusState(); }
    };

    private RichEditorHistory.FocusState captureFocusState() {
        View f = findFocus();
        if (!(f instanceof RichEditText)) return RichEditorHistory.FocusState.NONE;
        RichEditText et = (RichEditText) f;
        int s = et.getSelectionStart();
        int e = et.getSelectionEnd();
        RichTableCell rtc = findTableCellAncestor(et);
        if (rtc != null && rtc.getRow() != null) {
            if (et == rtc.getTitleEditText()) {
                return new RichEditorHistory.FocusState(rtc.getRow().id, rtc.titleChildPos(), s, e);
            }
            int childIndex = -1;
            RichTableCellHost host = rtc.findHostContaining(et);
            if (host != null) childIndex = rtc.childPosForAnchor(host.cell);
            return new RichEditorHistory.FocusState(rtc.getRow().id, childIndex, s, e);
        }
        RichCaptionHost ch = findCaptionHostAncestor(et);
        if (ch != null && ch.getRow() != null) {
            return new RichEditorHistory.FocusState(ch.getRow().id, -1, s, e);
        }
        View p = et;
        while (p != null && !(p instanceof RichTextCell)) {
            android.view.ViewParent vp = p.getParent();
            p = vp instanceof View ? (View) vp : null;
        }
        if (p instanceof RichTextCell && ((RichTextCell) p).getRow() != null) {
            return new RichEditorHistory.FocusState(((RichTextCell) p).getRow().id, -1, s, e);
        }
        return RichEditorHistory.FocusState.NONE;
    }

    private void restoreFromHistory(List<BlockRow> newRows, RichEditorHistory.FocusState focus) {
        textSelectionHelper.clear();
        rows.clear();
        rows.addAll(newRows);
        renumberAllRuns();
        adapter.update(false);
        if (focus != null && focus.rowId >= 0) {
            post(() -> focusFromHistory(focus));
        }
        delegate.onContentChanged();
    }

    private void focusFromHistory(RichEditorHistory.FocusState focus) {
        int idx = indexOfRowId(focus.rowId);
        if (idx < 0) return;
        View v = layoutManager.findViewByPosition(idx);
        if (v instanceof RichTextCell) {
            RichTextCell c = (RichTextCell) v;
            c.requestEditFocus();
            RichEditText et = c.getEditText();
            int len = et.length();
            int start = Math.max(0, Math.min(focus.selStart, len));
            int end = Math.max(0, Math.min(focus.selEnd, len));
            et.setSelection(start, end);
        } else if (v instanceof RichTableCell) {
            RichTableCell rtc = (RichTableCell) v;
            RichEditText et = rtc.editTextForChildPos(focus.childIndex);
            if (et == null) et = rtc.editTextForChildPos(rtc.titleChildPos());
            if (et == null) return;
            et.requestEditFocus();
            int len = et.length();
            int start = Math.max(0, Math.min(focus.selStart, len));
            int end = Math.max(0, Math.min(focus.selEnd, len));
            et.setSelection(start, end);
        } else if (v instanceof RichCaptionHost) {
            RichEditText et = ((RichCaptionHost) v).getCaptionEditText();
            et.requestEditFocus();
            int len = et.length();
            int start = Math.max(0, Math.min(focus.selStart, len));
            int end = Math.max(0, Math.min(focus.selEnd, len));
            et.setSelection(start, end);
        }
    }

    private int indexOfRowId(long id) {
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).id == id) return i;
        }
        return -1;
    }

    final ArrayList<BlockRow> itemRows = new ArrayList<>();

    BlockRow rowForCell(int cellPos) {
        return cellPos >= 0 && cellPos < itemRows.size() ? itemRows.get(cellPos) : null;
    }

    void assignContainers() {
        final ArrayList<RichContainer> detailsStack = new ArrayList<>();
        final ArrayList<Long> listId = new ArrayList<>();
        final ArrayList<Boolean> listOrdered = new ArrayList<>();
        final ArrayList<Integer> listCount = new ArrayList<>();
        final ArrayList<Long> listItemId = new ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            final BlockRow r = rows.get(i);
            r.path.clear();
            r.itemStart = false;
            if (r.detailsEnd) {
                if (!detailsStack.isEmpty()) detailsStack.remove(detailsStack.size() - 1);
                listId.clear();
                listOrdered.clear();
                listCount.clear();
                listItemId.clear();
                continue;
            }
            for (int q = 0; q < r.quoteIds.size(); q++) {
                r.path.add(RichContainer.quote(r.quoteIds.get(q)));
            }
            for (int d = 0; d < detailsStack.size(); d++) {
                r.path.add(detailsStack.get(d));
            }
            final int L = Math.max(0, r.level);
            if (L == 0) {
                listId.clear();
                listOrdered.clear();
                listCount.clear();
                listItemId.clear();
            } else {
                final boolean innerOrdered = r.num > 0;
                while (listId.size() > L) {
                    listId.remove(listId.size() - 1);
                    listOrdered.remove(listOrdered.size() - 1);
                    listCount.remove(listCount.size() - 1);
                    listItemId.remove(listItemId.size() - 1);
                }
                for (int d = 1; d <= L; d++) {
                    final int idx = d - 1;
                    final boolean innermost = d == L;
                    boolean open = idx < listId.size();
                    if (open && innermost && listOrdered.get(idx) != innerOrdered) {
                        open = false;
                    }
                    if (!open) {
                        while (listId.size() > idx) {
                            listId.remove(listId.size() - 1);
                            listOrdered.remove(listOrdered.size() - 1);
                            listCount.remove(listCount.size() - 1);
                            listItemId.remove(listItemId.size() - 1);
                        }
                        listId.add(RichContainer.newId());
                        listOrdered.add(innermost ? innerOrdered : false);
                        listCount.add(0);
                        listItemId.add(0L);
                    }
                    final long id = listId.get(idx);
                    if (innermost) {
                        final boolean startsItem = listItemId.get(idx) == 0L || !isNonText(r.block);
                        final long itemId;
                        final int num;
                        if (startsItem) {
                            num = listCount.get(idx) + 1;
                            listCount.set(idx, num);
                            itemId = r.id;
                            listItemId.set(idx, itemId);
                            r.itemStart = true;
                        } else {
                            num = listCount.get(idx);
                            itemId = listItemId.get(idx);
                        }
                        final RichContainer c = RichContainer.list(id, itemId, innerOrdered, r.checkbox, r.checked);
                        c.itemNum = num;
                        r.path.add(c);
                    } else {
                        r.path.add(RichContainer.list(id, 0, listOrdered.get(idx), false, false));
                    }
                }
            }
            if (isDetailsHeader(r)) {
                final boolean open = ((TL_iv.pageBlockDetails) r.block).open;
                detailsStack.add(RichContainer.details(RichContainer.newId(), open));
                listId.clear();
                listOrdered.clear();
                listCount.clear();
                listItemId.clear();
            }
            if (RichTextCell.isQuoteBlock(r.block)) {
                r.path.add(RichContainer.quote(RichContainer.newId()));
            }
        }
    }

    private ReplyMessageLine quoteLine;

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawQuoteBars(canvas);
        super.dispatchDraw(canvas);
    }

    private BlockRow rowOfChild(View child) {
        final int pos = getChildAdapterPosition(child);
        return pos >= 0 && pos < itemRows.size() ? itemRows.get(pos) : null;
    }

    private void drawQuoteBars(Canvas canvas) {
        final int childCount = getChildCount();
        if (childCount == 0) return;
        int maxDepth = 0;
        for (int i = 0; i < childCount; i++) {
            final BlockRow r = rowOfChild(getChildAt(i));
            if (r != null && r.quoteIds.size() > maxDepth) maxDepth = r.quoteIds.size();
        }
        if (maxDepth == 0) return;
        if (quoteLine == null) {
            quoteLine = new ReplyMessageLine(this);
            quoteLine.check(null, null, null, resourcesProvider, ReplyMessageLine.TYPE_QUOTE);
        }
        for (int d = 0; d < maxDepth; d++) {
            boolean inSeg = false;
            long segId = 0;
            float top = 0, bottom = 0, alpha = 1f;
            BlockRow firstR = null, lastR = null;
            for (int i = 0; i <= childCount; i++) {
                Long id = null;
                View child = null;
                BlockRow r = null;
                if (i < childCount) {
                    child = getChildAt(i);
                    r = rowOfChild(child);
                    if (r != null && d < r.quoteIds.size()) id = r.quoteIds.get(d);
                }
                if (inSeg && (id == null || id.longValue() != segId)) {
                    drawQuoteContainer(canvas, d, top, bottom, alpha, quoteBgVinset(d, firstR, true), quoteBgVinset(d, lastR, false));
                    inSeg = false;
                }
                if (child != null && id != null) {
                    if (!inSeg) {
                        inSeg = true;
                        segId = id;
                        top = Float.MAX_VALUE;
                        bottom = -Float.MAX_VALUE;
                        alpha = 1f;
                        firstR = r;
                    }
                    lastR = r;
                    if (r == null || r != draggingRow) {
                        final float cTop = child.getY();
                        final float cBottom = cTop + child.getHeight();
                        if (cTop < top) top = cTop;
                        if (cBottom > bottom) bottom = cBottom;
                        alpha = Math.min(alpha, child.getAlpha());
                    }
                }
            }
        }
    }

    private void drawQuoteContainer(Canvas canvas, int depth, float top, float bottom, float alpha, int topInset, int bottomInset) {
        if (bottom - top <= dp(4)) return;
        final int step = depth * dp(RichBlockChrome.QUOTE_STEP_DP);
        final int left = dp(RichBlockChrome.QUOTE_GUTTER_DP) + step;
        final int right = getWidth() - dp(RichBlockChrome.QUOTE_GUTTER_DP) - step;
        if (right - left <= dp(8)) return;
        final float rad = (float) Math.floor(SharedConfig.bubbleRadius / 3f);
        AndroidUtilities.rectTmp.set(left, top + topInset, right, bottom - bottomInset);
        quoteLine.drawBackground(canvas, AndroidUtilities.rectTmp, rad, rad, rad, alpha);
        quoteLine.drawLine(canvas, AndroidUtilities.rectTmp, alpha);
    }

    private int quoteBgVinset(int d, BlockRow edgeRow, boolean isTop) {
        if (edgeRow == null) return dp(2);
        final int edge = isTop ? edgeRow.quoteTopEdge : edgeRow.quoteBottomEdge;
        final int common = edgeRow.quoteIds.size() - edge;
        return dp(2) + Math.max(0, d - common) * dp(RichBlockChrome.QUOTE_NEST_VPAD_DP);
    }

    private void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        assignContainers();
        itemRows.clear();
        final HashMap<Long, Integer> quoteRowCount = new HashMap<>();
        int paragraphCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            final BlockRow r = rows.get(i);
            if (r.detailsEnd) continue;
            for (int q = 0; q < r.quoteIds.size(); q++) quoteRowCount.merge(r.quoteIds.get(q), 1, Integer::sum);
            if (r.block instanceof TL_iv.pageBlockParagraph) paragraphCount++;
        }
        final boolean singleParagraph = paragraphCount == 1;
        BlockRow prevContentRow = null;
        final ArrayList<BlockRow> stack = new ArrayList<>();
        int collapsedDepth = -1;
        boolean sectionOpen = false;
        for (int i = 0; i < rows.size(); i++) {
            BlockRow row = rows.get(i);
            if (row.detailsEnd) {
                final boolean endHidden = collapsedDepth != -1;
                if (!stack.isEmpty()) stack.remove(stack.size() - 1);
                if (collapsedDepth != -1 && stack.size() < collapsedDepth) {
                    collapsedDepth = -1;
                    continue;
                }
                if (!endHidden) {
                    if (sectionOpen) { adapter.reorderSectionEnd(); sectionOpen = false; }
                    items.add(RichDetailsEndCell.Factory.of(row));
                    itemRows.add(row);
                }
                continue;
            }
            final boolean hidden = collapsedDepth != -1;
            if (isDetailsHeader(row)) {
                if (!hidden) {
                    final boolean open = ((TL_iv.pageBlockDetails) row.block).open;
                    final boolean draggableUnit = stack.isEmpty() && !open;
                    if (draggableUnit) {
                        if (!sectionOpen) { adapter.reorderSectionStart(); sectionOpen = true; }
                    } else if (sectionOpen) {
                        adapter.reorderSectionEnd();
                        sectionOpen = false;
                    }
                    items.add(RichDetailsCell.Factory.of(row, detailsDelegate));
                    itemRows.add(row);
                    if (!open) collapsedDepth = stack.size() + 1;
                }
                stack.add(row);
                continue;
            }
            if (hidden) continue;
            if (prevContentRow != null && willInjectAuthors(prevContentRow, row.quoteIds, quoteRowCount) && sectionOpen) {
                adapter.reorderSectionEnd(); sectionOpen = false;
            }
            if (prevContentRow != null) injectClosingQuoteAuthors(prevContentRow, row.quoteIds, quoteRowCount, items);
            if (stack.isEmpty()) {
                if (!sectionOpen) { adapter.reorderSectionStart(); sectionOpen = true; }
            } else if (sectionOpen) {
                adapter.reorderSectionEnd(); sectionOpen = false;
            }
            if (row.block instanceof TL_iv.pageBlockDivider) {
                items.add(RichDividerCell.Factory.of(row, dividerDelegate));
            } else if (isMedia(row.block)) {
                items.add(RichMediaCell.Factory.of(row, mediaDelegate));
            } else if (row.block instanceof TL_iv.pageBlockAudio) {
                items.add(RichAudioCell.Factory.of(row, audioDelegate));
            } else if (row.block instanceof TL_iv.pageBlockMap) {
                items.add(RichMapCell.Factory.of(row, mapDelegate));
            } else if (row.block instanceof TL_iv.pageBlockMath) {
                items.add(RichMathCell.Factory.of(row, mathDelegate));
            } else if (row.block instanceof TL_iv.pageBlockTable) {
                items.add(RichTableCell.Factory.of(row, tableDelegate));
            } else {
                row.firstBlock = i == 0;
                row.singleParagraph = singleParagraph && row.block instanceof TL_iv.pageBlockParagraph;
                boolean forceHint = false;
                if (rows.size() == 2 && i == 1 && row.block instanceof TL_iv.pageBlockParagraph && rows.get(0).block instanceof TL_iv.pageBlockHeading1) {
                    forceHint = true;
                }
                items.add(RichTextCell.Factory.of(row, cellDelegate, forceHint));
            }
            itemRows.add(row);
            prevContentRow = row;
        }
        if (sectionOpen) adapter.reorderSectionEnd();
        if (prevContentRow != null) injectClosingQuoteAuthors(prevContentRow, EMPTY_QUOTE_IDS, quoteRowCount, items);
        markQuoteEdges();
    }

    private void markQuoteEdges() {
        for (int i = 0; i < itemRows.size(); i++) {
            final BlockRow r = itemRows.get(i);
            r.quoteFirst = false;
            r.quoteLast = false;
            r.quoteTopEdge = 0;
            r.quoteBottomEdge = 0;
            final int d = r.quoteIds.size();
            if (d == 0) continue;
            final BlockRow prev = i > 0 ? itemRows.get(i - 1) : null;
            final BlockRow next = i + 1 < itemRows.size() ? itemRows.get(i + 1) : null;
            r.quoteTopEdge = d - quoteCommonPrefixLen(r, prev);
            r.quoteBottomEdge = d - quoteCommonPrefixLen(r, next);
            r.quoteFirst = r.quoteTopEdge > 0;
            r.quoteLast = r.quoteBottomEdge > 0;
        }
    }

    private static int quoteCommonPrefixLen(BlockRow a, BlockRow b) {
        if (a == null || b == null) return 0;
        final int n = Math.min(a.quoteIds.size(), b.quoteIds.size());
        int i = 0;
        while (i < n && a.quoteIds.get(i).equals(b.quoteIds.get(i))) i++;
        return i;
    }

    private static final ArrayList<Long> EMPTY_QUOTE_IDS = new ArrayList<>();

    private static boolean sameQuoteIds(BlockRow a, BlockRow b) {
        if (a == null || b == null || a.quoteIds.size() != b.quoteIds.size()) return false;
        for (int i = 0; i < a.quoteIds.size(); i++) {
            if (!a.quoteIds.get(i).equals(b.quoteIds.get(i))) return false;
        }
        return true;
    }

    private boolean willInjectAuthors(BlockRow prev, ArrayList<Long> nextQuoteIds, Map<Long, Integer> counts) {
        final int pn = prev.quoteIds.size();
        int common = 0;
        while (common < pn && common < nextQuoteIds.size()
            && prev.quoteIds.get(common).equals(nextQuoteIds.get(common))) common++;
        for (int d = pn - 1; d >= common; d--) {
            final long qid = prev.quoteIds.get(d);
            final Integer c = counts.get(qid);
            if (c != null && (c >= 2 || d > 0 || quoteAuthors.containsKey(qid))) return true;
        }
        return false;
    }

    private boolean normalizeQuoteMembership(BlockRow dragged) {
        if (dragged == null) return false;
        final int mi = rows.indexOf(dragged);
        if (mi < 0) return false;
        final ArrayList<Long> above = mi > 0 ? rows.get(mi - 1).quoteIds : EMPTY_QUOTE_IDS;
        final ArrayList<Long> below = mi + 1 < rows.size() ? rows.get(mi + 1).quoteIds : EMPTY_QUOTE_IDS;
        final ArrayList<Long> target = above.size() >= below.size() ? above : below;
        if (dragged.quoteIds.equals(target)) return false;
        dragged.quoteIds.clear();
        dragged.quoteIds.addAll(target);
        return true;
    }

    private void injectClosingQuoteAuthors(BlockRow prev, ArrayList<Long> nextQuoteIds, Map<Long, Integer> counts, ArrayList<UItem> items) {
        final int pn = prev.quoteIds.size();
        int common = 0;
        while (common < pn && common < nextQuoteIds.size()
            && prev.quoteIds.get(common).equals(nextQuoteIds.get(common))) common++;
        for (int d = pn - 1; d >= common; d--) {
            final long qid = prev.quoteIds.get(d);
            final Integer c = counts.get(qid);
            if (c == null || (c < 2 && d == 0 && !quoteAuthors.containsKey(qid))) continue;
            final BlockRow authorRow = new BlockRow(new TL_iv.pageBlockParagraph());
            authorRow.authorQuoteId = qid;
            for (int k = 0; k <= d; k++) authorRow.quoteIds.add(prev.quoteIds.get(k));
            items.add(RichQuoteAuthorCell.Factory.of(authorRow, quoteAuthorDelegate));
            itemRows.add(authorRow);
        }
    }

    private void onRowsReordered(int id, ArrayList<UItem> items) {
        ArrayList<BlockRow> moved = new ArrayList<>(items.size());
        for (UItem item : items) {
            if (item.object instanceof BlockRow) moved.add((BlockRow) item.object);
        }
        if (moved.size() < 2) return;
        final ArrayList<ArrayList<BlockRow>> units = new ArrayList<>();
        int minStart = Integer.MAX_VALUE, maxEndExcl = -1, total = 0;
        for (BlockRow r : moved) {
            final int idx = rows.indexOf(r);
            if (idx < 0) return;
            int endExcl = idx + 1;
            if (isDetailsHeader(r) && !((TL_iv.pageBlockDetails) r.block).open) {
                final int end = matchingDetailsEnd(idx);
                endExcl = end >= rows.size() ? rows.size() : end + 1;
            }
            units.add(new ArrayList<>(rows.subList(idx, endExcl)));
            minStart = Math.min(minStart, idx);
            maxEndExcl = Math.max(maxEndExcl, endExcl);
            total += endExcl - idx;
        }
        if (total != maxEndExcl - minStart) return;
        final ArrayList<BlockRow> rebuilt = new ArrayList<>(total);
        for (ArrayList<BlockRow> u : units) rebuilt.addAll(u);
        boolean changed = false;
        for (int k = 0; k < rebuilt.size(); k++) {
            if (rows.get(minStart + k) != rebuilt.get(k)) { changed = true; break; }
        }
        if (!changed) return;
        if (history != null) history.flush();
        for (int k = 0; k < rebuilt.size(); k++) {
            rows.set(minStart + k, rebuilt.get(k));
        }
        final boolean quotesChanged = normalizeQuoteMembership(draggingRow);
        final boolean collapsed = collapseSingleBlockQuotes();
        normalizeLevels();
        renumberAllRuns();
        assignContainers();
        if (quotesChanged || collapsed) {
            adapter.update(true);
            resyncInsetCells(true);
        } else {
            resyncInsetCells(true);
        }
        if (history != null) history.record();
    }

    private void resyncInsetCells(boolean animated) {
        for (int i = 0; i < getChildCount(); i++) {
            final View c = getChildAt(i);
            if (c instanceof RichInsetCell) ((RichInsetCell) c).resyncBlockInset(animated);
        }
        invalidate();
    }

    private void normalizeLevels() {
        for (int i = 0; i < rows.size(); i++) {
            final BlockRow r = rows.get(i);
            if (r.detailsEnd || isDetailsHeader(r)) continue;
            final BlockRow prev = i > 0 ? rows.get(i - 1) : null;
            final int prevLevel = prev != null ? Math.max(0, prev.level) : 0;
            if (isNonText(r.block)) {
                r.level = prevLevel;
                if (r.level > 0) {
                    r.num = prev.num > 0 ? 1 : 0;
                    r.checkbox = false;
                    r.checked = false;
                }
            } else if (r.level > prevLevel + 1) {
                r.level = prevLevel + 1;
            }
            if (r.level <= 0) {
                r.level = 0;
                r.num = 0;
                r.checkbox = false;
                r.checked = false;
            }
        }
    }

    private static boolean isMedia(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockPhoto || b instanceof TL_iv.pageBlockVideo || isGallery(b);
    }

    static boolean isGallery(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockCollage || b instanceof TL_iv.pageBlockSlideshow;
    }

    static ArrayList<TL_iv.PageBlock> galleryItems(TL_iv.PageBlock b) {
        if (b instanceof TL_iv.pageBlockCollage) return ((TL_iv.pageBlockCollage) b).items;
        if (b instanceof TL_iv.pageBlockSlideshow) return ((TL_iv.pageBlockSlideshow) b).items;
        return null;
    }

    static List<MediaUploadState> mediasOf(BlockRow r) {
        if (r == null) return Collections.emptyList();
        if (isGallery(r.block)) {
            return r.medias != null ? r.medias : Collections.emptyList();
        }
        if (r.media != null) return Collections.singletonList(r.media);
        return Collections.emptyList();
    }

    private static long mediaIdOf(TL_iv.PageBlock item) {
        if (item instanceof TL_iv.pageBlockPhoto) return ((TL_iv.pageBlockPhoto) item).photo_id;
        if (item instanceof TL_iv.pageBlockVideo) return ((TL_iv.pageBlockVideo) item).video_id;
        return 0;
    }

    private static TL_iv.PageBlock itemBlockFor(BlockRow row, MediaUploadState media) {
        if (isGallery(row.block)) {
            final ArrayList<TL_iv.PageBlock> items = galleryItems(row.block);
            final int idx = row.medias != null ? row.medias.indexOf(media) : -1;
            return idx >= 0 && items != null && idx < items.size() ? items.get(idx) : null;
        }
        return row.block;
    }

    private void onItemClick(UItem item, View view, int position, float x, float y) {
        if (view instanceof RichTextCell) {
            ((RichTextCell) view).requestEditFocus();
        }
    }

    public boolean handleKeyEvent(KeyEvent ev) {
        if (ev.getAction() != KeyEvent.ACTION_DOWN) return false;
        int code = ev.getKeyCode();
        boolean shift = ev.isShiftPressed();
        boolean ctrl = ev.isCtrlPressed();
        boolean alt = ev.isAltPressed();
        boolean helperActive = textSelectionHelper.isInSelectionMode();

        if (ctrl && !alt && history != null) {
            if (code == KeyEvent.KEYCODE_Z && !shift) { history.undo(); return true; }
            if ((code == KeyEvent.KEYCODE_Z && shift) || code == KeyEvent.KEYCODE_Y) { history.redo(); return true; }
        }

        if (ctrl && !alt) {
            int styleFlag = 0;
            boolean link = false;
            if (!shift) {
                switch (code) {
                    case KeyEvent.KEYCODE_B: styleFlag = RichTextStyle.BOLD; break;
                    case KeyEvent.KEYCODE_I: styleFlag = RichTextStyle.ITALIC; break;
                    case KeyEvent.KEYCODE_U: styleFlag = RichTextStyle.UNDERLINE; break;
                    case KeyEvent.KEYCODE_E: styleFlag = RichTextStyle.MONO; break;
                    case KeyEvent.KEYCODE_K: link = true; break;
                }
            } else {
                switch (code) {
                    case KeyEvent.KEYCODE_X:
                    case KeyEvent.KEYCODE_S: styleFlag = RichTextStyle.STRIKE; break;
                    case KeyEvent.KEYCODE_M: styleFlag = RichTextStyle.MONO; break;
                    case KeyEvent.KEYCODE_P: styleFlag = RichTextStyle.SPOILER; break;
                }
            }
            if (link) { onLinkClicked(); return true; }
            if (styleFlag != 0) { onFormattingClicked(styleFlag); return true; }
        }

        if (code == KeyEvent.KEYCODE_ESCAPE && helperActive) {
            textSelectionHelper.clear();
            return true;
        }

        if (helperActive) {
            if (ctrl && !shift && code == KeyEvent.KEYCODE_C) { copyHelperSelection(); return true; }
            if (ctrl && !shift && code == KeyEvent.KEYCODE_X) { cutHelperSelection(); return true; }
            if (ctrl && code == KeyEvent.KEYCODE_V) { pasteAtHelperSelection(); return true; }
            if (code == KeyEvent.KEYCODE_DEL || code == KeyEvent.KEYCODE_FORWARD_DEL) {
                deleteHelperSelection();
                return true;
            }
            if (code == KeyEvent.KEYCODE_ENTER) {
                replaceHelperSelectionWith("\n");
                return true;
            }
            if (!ctrl && !alt) {
                int uc = ev.getUnicodeChar(ev.getMetaState());
                if (uc >= 32) {
                    replaceHelperSelectionWith(String.valueOf((char) uc));
                    return true;
                }
            }
        }

        if (!helperActive && shift && isArrowKey(code)) {
            if (tryEscalateSelectionFromCaret(code)) return true;
        }
        if (helperActive && shift && isArrowKey(code)) {
            if (tryExtendSelectionAcrossCells(code, ctrl || alt)) return true;
        }
        if (helperActive && !shift && !ctrl && !alt && isArrowKey(code)) {
            boolean toEnd = code == KeyEvent.KEYCODE_DPAD_RIGHT || code == KeyEvent.KEYCODE_DPAD_DOWN;
            restoreFocusCell = toEnd ? textSelectionHelper.getEndCell() : textSelectionHelper.getStartCell();
            restoreFocusOffset = toEnd ? textSelectionHelper.getEndOffset() : textSelectionHelper.getStartOffset();
            restoreFocusChildPosition = toEnd ? textSelectionHelper.getEndChildPosition() : textSelectionHelper.getStartChildPosition();
            textSelectionHelper.clear();
            return true;
        }
        if (ctrl && code == KeyEvent.KEYCODE_A) {
            if (textSelectionHelper.expandSelectionToWholeCurrentBlock()) return true;
            if (tryEscalateSelectAll()) return true;
        }

        if (!helperActive && !shift && !ctrl && !alt && (code == KeyEvent.KEYCODE_DPAD_DOWN || code == KeyEvent.KEYCODE_DPAD_UP)) {
            if (tryPlainArrowAcrossCells(code)) return true;
        }

        if (!helperActive && !ctrl && !alt && code == KeyEvent.KEYCODE_TAB) {
            View focused = findFocus();
            if (focused instanceof RichEditText) {
                RichTableCell rtc = findTableCellAncestor(focused);
                if (rtc != null) {
                    if (focused == rtc.getTitleEditText()) {
                        if (!shift && rtc.focusFirstCell()) return true;
                    } else {
                        RichTableCellHost host = rtc.findHostContaining(focused);
                        if (host != null && rtc.moveFocusByTab(host, shift)) return true;
                    }
                }
            }
        }

        if (code == KeyEvent.KEYCODE_TAB && helperActive) {
            int s = textSelectionHelper.getStartCell();
            int e = textSelectionHelper.getEndCell();
            if (s >= 0 && e >= s) {
                if (s == e) {
                    final BlockRow row = rowForCell(s);
                    if (row != null) onCellIndent(row, shift);
                } else {
                    final int sr = rows.indexOf(rowForCell(s));
                    final int er = rows.indexOf(rowForCell(e));
                    if (sr >= 0 && er >= sr) rangeIndent(sr, er, shift);
                }
            }
            return true;
        }
        return false;
    }

    void addBlock(TL_iv.PageBlock block) {
        if (history != null) history.flush();
        final BlockRow row;
        BlockRow focused = findFocusedRow();
        if (focused == null) focused = pendingInsertRow;
        int idx = focused != null ? rows.indexOf(focused) : -1;

        if (idx >= 0 && focused.block instanceof TL_iv.pageBlockBlockquote) {
            final long qid = RichContainer.newId();
            final TL_iv.RichText author = ((TL_iv.pageBlockBlockquote) focused.block).caption;
            if (author != null && !(author instanceof TL_iv.textEmpty)) quoteAuthors.put(qid, author);
            focused.quoteIds.add(qid);
            focused.block = new TL_iv.pageBlockParagraph();
        }
        final boolean inQuote = idx >= 0 && !focused.quoteIds.isEmpty();

        if (idx >= 0
                && focused.block instanceof TL_iv.pageBlockParagraph
                && focused.media == null
                && RichTextCell.readPlainText(focused.block).isEmpty()) {
            focused.block = block;
            row = focused;
        } else {
            row = new BlockRow(block);
            if (inQuote) row.quoteIds.addAll(focused.quoteIds);
            if (idx >= 0) rows.add(idx + 1, row);
            else rows.add(row);
        }
        pendingInsertRow = row;
        BlockRow focusTarget = row;
        if (hasCaption(block)) {
            int ri = rows.indexOf(row);
            if (ri + 1 >= rows.size() || isNonText(rows.get(ri + 1).block)) {
                final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
                if (inQuote) para.quoteIds.addAll(row.quoteIds);
                rows.add(ri + 1, para);
                focusTarget = para;
            } else {
                focusTarget = rows.get(ri + 1);
            }
        }
        adapter.update(false);
        if (history != null) history.record();
        final BlockRow ft = focusTarget;
        post(() -> {
            View nv = findViewByItemObject(ft);
            if (nv instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) nv;
                c.requestEditFocus();
                c.getEditText().setSelection(0);
            }
        });
    }

    void insertDetails() {
        if (history != null) history.flush();
        final TL_iv.pageBlockDetails details = new TL_iv.pageBlockDetails();
        details.open = true;
        details.title = new TL_iv.textEmpty();
        final BlockRow header = new BlockRow(details);
        final BlockRow inner = new BlockRow(new TL_iv.pageBlockParagraph());
        final BlockRow end = newDetailsEndRow();

        final BlockRow focused = findFocusedRow();
        int idx = focused != null ? rows.indexOf(focused) : -1;
        int at;
        if (focused != null
                && focused.block instanceof TL_iv.pageBlockParagraph
                && focused.media == null
                && !focused.detailsEnd
                && RichTextCell.readPlainText(focused.block).isEmpty()) {
            at = idx;
            rows.remove(idx);
        } else if (idx >= 0) {
            at = idx + 1;
        } else {
            at = rows.size();
        }
        rows.add(at, end);
        rows.add(at, inner);
        rows.add(at, header);

        adapter.update(false);
        if (history != null) history.record();
        post(() -> {
            View v = findViewByItemObject(header);
            if (v instanceof RichDetailsCell) ((RichDetailsCell) v).requestEditFocus();
        });
    }

    void attachAudio(MessageObject mo) {
        if (mo == null) return;
        final TLRPC.Document doc = mo.getDocument();
        if (doc == null) return;
        if (history != null) history.flush();

        final TL_iv.pageBlockAudio block = new TL_iv.pageBlockAudio();
        final BlockRow row = new BlockRow(block);
        row.media = new MediaUploadState();
        row.media.isAudio = true;

        final boolean local = doc.id == 0 || doc.dc_id == 0 || doc.access_hash == 0;
        if (local) {
            final String path = mo.messageOwner != null ? mo.messageOwner.attachPath : null;
            if (TextUtils.isEmpty(path)) return;
            row.media.audioDisplayDocument = doc;
            row.media.localPath = path;
            row.media.state = MediaUploadState.STATE_UPLOADING;
            row.media.progress = 0f;
        } else {
            row.media.document = doc;
            row.media.audioDisplayDocument = doc;
            row.media.state = MediaUploadState.STATE_DONE;
            block.audio_id = doc.id;
        }

        insertPreparedRow(row);
        if (local) startAudioUpload(row, row.media.localPath, doc);
        if (history != null) history.record();
        delegate.onContentChanged();
    }

    private void insertPreparedRow(BlockRow row) {
        BlockRow focused = findFocusedRow();
        if (focused == null) focused = pendingInsertRow;
        int idx = focused != null ? rows.indexOf(focused) : -1;

        boolean promoted = false;
        if (idx >= 0 && focused.block instanceof TL_iv.pageBlockBlockquote) {
            final long qid = RichContainer.newId();
            final TL_iv.RichText author = ((TL_iv.pageBlockBlockquote) focused.block).caption;
            if (author != null && !(author instanceof TL_iv.textEmpty)) quoteAuthors.put(qid, author);
            focused.quoteIds.add(qid);
            focused.block = new TL_iv.pageBlockParagraph();
            promoted = true;
        }
        final boolean inQuote = idx >= 0 && !focused.quoteIds.isEmpty();
        if (inQuote) {
            row.quoteIds.clear();
            row.quoteIds.addAll(focused.quoteIds);
        }

        if (idx >= 0
                && focused.block instanceof TL_iv.pageBlockParagraph
                && focused.media == null
                && RichTextCell.readPlainText(focused.block).isEmpty()) {
            rows.set(idx, row);
        } else if (idx >= 0) {
            rows.add(idx + 1, row);
        } else {
            rows.add(row);
        }
        pendingInsertRow = row;
        int ri = rows.indexOf(row);
        if (ri >= rows.size() - 1 || isNonText(rows.get(ri + 1).block)) {
            final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
            if (inQuote) para.quoteIds.addAll(row.quoteIds);
            rows.add(ri + 1, para);
        }
        adapter.update(!promoted);
        final int paraIdx = rows.indexOf(row) + 1;
        if (paraIdx > 0 && paraIdx < rows.size() && !isNonText(rows.get(paraIdx).block)) {
            final BlockRow para = rows.get(paraIdx);
            post(() -> {
                View nv = findViewByItemObject(para);
                if (nv instanceof RichTextCell) {
                    RichTextCell c = (RichTextCell) nv;
                    c.requestEditFocus();
                    c.getEditText().setSelection(0);
                }
            });
        }
    }

    private void startAudioUpload(BlockRow row, String path, TLRPC.Document localDoc) {
        final MediaUploadState media = row.media;
        RichMediaUploader existing = uploaders.remove(media);
        if (existing != null) existing.cancel();
        RichMediaUploader uploader = RichMediaUploader.forAudio(currentAccount, path, localDoc, new RichMediaUploader.Listener() {
            @Override
            public void onProgress(float progress) {
                media.progress = progress;
                invalidateAudioCell(row);
                delegate.onContentChanged();
            }
            @Override
            public void onAudioUploaded(TLRPC.Document doc) {
                media.document = doc;
                media.audioDisplayDocument = doc;
                media.state = MediaUploadState.STATE_DONE;
                if (row.block instanceof TL_iv.pageBlockAudio) {
                    ((TL_iv.pageBlockAudio) row.block).audio_id = doc.id;
                }
                uploaders.remove(media);
                adapter.update(false);
                delegate.onContentChanged();
            }
            @Override
            public void onError() {
                media.state = MediaUploadState.STATE_ERROR;
                uploaders.remove(media);
                int idx = rows.indexOf(row);
                if (idx >= 0) {
                    rows.remove(idx);
                    adapter.update(true);
                }
                delegate.onContentChanged();
            }
        });
        uploaders.put(media, uploader);
        uploader.start();
    }

    private void cancelAudioUpload(BlockRow row) {
        RichMediaUploader u = uploaders.remove(row.media);
        if (u != null) u.cancel();
        int idx = rows.indexOf(row);
        if (idx >= 0) {
            if (history != null) history.flush();
            rows.remove(idx);
            adapter.update(true);
            if (history != null) history.record();
        }
        delegate.onContentChanged();
    }

    private void invalidateAudioCell(BlockRow row) {
        View v = findViewByItemObject(row);
        if (v instanceof RichAudioCell) {
            ((RichAudioCell) v).updateButtonState(false);
            v.invalidate();
        }
    }

    private static MediaUploadState newUploadingMedia(MediaController.PhotoEntry entry, String path) {
        final MediaUploadState media = new MediaUploadState();
        media.isVideo = entry.isVideo;
        media.localPath = path;
        media.width = entry.width;
        media.height = entry.height;
        media.duration = entry.duration;
        final boolean baked = entry.imagePath != null && path != null && path.equals(entry.imagePath);
        media.orientation = baked ? 0 : entry.orientation;
        media.invert = baked ? 0 : entry.invert;
        media.state = MediaUploadState.STATE_UPLOADING;
        media.progress = 0f;
        return media;
    }

    void attachMedia(MediaController.PhotoEntry entry) {
        if (entry == null) return;
        final String path = entry.imagePath != null ? entry.imagePath : entry.path;
        if (TextUtils.isEmpty(path)) return;
        if (history != null) history.flush();

        final boolean convertToVideo = RichMediaConverter.hasAnimatedMediaEntities(entry);
        final boolean asVideo = entry.isVideo || convertToVideo;
        final TL_iv.PageBlock block = asVideo ? new TL_iv.pageBlockVideo() : new TL_iv.pageBlockPhoto();
        final BlockRow row = new BlockRow(block);
        row.media = newUploadingMedia(entry, path);

        insertPreparedRow(row);
        if (convertToVideo) {
            startMediaConvertAndUpload(row, row.media, entry);
        } else {
            startMediaUpload(row, row.media, path, entry.isVideo, entry.width, entry.height, entry.duration);
        }
        if (history != null) history.record();
        delegate.onContentChanged();
    }

    void addMediaToRow(BlockRow row, MediaController.PhotoEntry entry) {
        if (row == null || entry == null) return;
        if (!isMedia(row.block)) return;
        final String path = entry.imagePath != null ? entry.imagePath : entry.path;
        if (TextUtils.isEmpty(path)) return;
        if (history != null) history.flush();

        final MediaUploadState media = newUploadingMedia(entry, path);
        final boolean convertToVideo = RichMediaConverter.hasAnimatedMediaEntities(entry);
        final boolean asVideo = entry.isVideo || convertToVideo;
        final TL_iv.PageBlock itemBlock = asVideo ? new TL_iv.pageBlockVideo() : new TL_iv.pageBlockPhoto();

        if (isGallery(row.block)) {
            galleryItems(row.block).add(itemBlock);
            if (row.medias == null) row.medias = new ArrayList<>();
            row.medias.add(media);
        } else if (row.media == null || row.media.state == MediaUploadState.STATE_EMPTY) {
            row.block = itemBlock;
            row.media = media;
        } else {
            final TL_iv.pageBlockCollage col = new TL_iv.pageBlockCollage();
            col.caption = row.block.caption;
            RichCaptionController.ensureCaption(col);
            col.items = new ArrayList<>();
            col.items.add(row.block);
            col.items.add(itemBlock);
            row.medias = new ArrayList<>();
            row.medias.add(row.media);
            row.medias.add(media);
            row.media = null;
            row.block = col;
        }

        adapter.update(false);
        refreshMediaCell(row);
        if (convertToVideo) {
            startMediaConvertAndUpload(row, media, entry);
        } else {
            startMediaUpload(row, media, path, entry.isVideo, entry.width, entry.height, entry.duration);
        }
        if (history != null) history.record();
        delegate.onContentChanged();
    }

    private int lastExternalImageId = -1;
    void attachExternalMedia(Uri uri) {
        if (uri == null) return;
        final BlockRow target = pendingMediaRow;
        pendingMediaRow = null;
        final Context context = getContext();
        if (context == null) return;
        String type = null;
        try {
            type = context.getContentResolver().getType(uri);
        } catch (Exception e) {
            FileLog.e(e);
        }
        final String mime = type;
        final boolean isVideo = mime != null ? mime.startsWith("video") : uri.toString().contains("video");
        final int imageId = lastExternalImageId--;
        Utilities.globalQueue.postRunnable(() -> {
            String path = null;
            try {
                path = AndroidUtilities.getPath(uri);
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (path == null || !(new File(path).exists())) {
                path = copyUriToCache(uri, isVideo, mime, imageId);
            }
            if (path == null || !(new File(path).exists())) {
                return;
            }
            int width = 0, height = 0, duration = 0;
            if (isVideo) {
                MediaMetadataRetriever retriever = null;
                try {
                    retriever = new MediaMetadataRetriever();
                    retriever.setDataSource(path);
                    final String w = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH);
                    final String h = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
                    final String d = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION);
                    if (w != null) width = Integer.parseInt(w);
                    if (h != null) height = Integer.parseInt(h);
                    if (d != null) duration = (int) Math.ceil(Long.parseLong(d) / 1000.0);
                } catch (Exception e) {
                    FileLog.e(e);
                } finally {
                    try { if (retriever != null) retriever.release(); } catch (Exception ignore) {}
                }
            }
            int orientation = 0, invert = 0;
            if (!isVideo) {
                try {
                    final BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeFile(path, opts);
                    width = opts.outWidth;
                    height = opts.outHeight;
                } catch (Exception e) {
                    FileLog.e(e);
                }
                try {
                    final android.util.Pair<Integer, Integer> o = AndroidUtilities.getImageOrientation(path);
                    orientation = o.first;
                    invert = o.second;
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }
            final String finalPath = path;
            final int finalWidth = width, finalHeight = height, finalDuration = duration;
            final int finalOrientation = orientation, finalInvert = invert;
            AndroidUtilities.runOnUIThread(() -> {
                final MediaController.PhotoEntry entry = new MediaController.PhotoEntry(0, imageId, 0, finalPath, isVideo ? finalDuration : 0, isVideo, finalWidth, finalHeight, 0);
                entry.setOrientation(finalOrientation, finalInvert);
                if (target != null && isMedia(target.block)) {
                    addMediaToRow(target, entry);
                } else {
                    attachMedia(entry);
                }
            });
        });
    }

    private String copyUriToCache(Uri uri, boolean isVideo, String mime, int imageId) {
        final Context context = getContext();
        if (context == null) return null;
        InputStream in = null;
        OutputStream out = null;
        try {
            in = context.getContentResolver().openInputStream(uri);
            if (in == null) return null;
            String ext = null;
            if (mime != null) {
                ext = android.webkit.MimeTypeMap.getSingleton().getExtensionFromMimeType(mime);
            }
            if (TextUtils.isEmpty(ext)) ext = isVideo ? "mp4" : "jpg";
            final File file = new File(FileLoader.getDirectory(FileLoader.MEDIA_DIR_CACHE), "rich_external_" + (-imageId) + "_" + SharedConfig.getLastLocalId() + "." + ext);
            out = new FileOutputStream(file);
            AndroidUtilities.copyFile(in, out);
            return file.getAbsolutePath();
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        } finally {
            try { if (in != null) in.close(); } catch (Exception ignore) {}
            try { if (out != null) out.close(); } catch (Exception ignore) {}
        }
    }

    private void startMediaConvertAndUpload(BlockRow row, MediaUploadState media, MediaController.PhotoEntry entry) {
        RichMediaUploader existingUpload = uploaders.remove(media);
        if (existingUpload != null) existingUpload.cancel();
        RichMediaConverter existingConvert = converters.remove(media);
        if (existingConvert != null) existingConvert.cancel();

        media.state = MediaUploadState.STATE_UPLOADING;
        media.progress = 0f;

        RichMediaConverter converter = new RichMediaConverter(currentAccount, entry, new RichMediaConverter.Listener() {
            @Override
            public void onProgress(float progress) {
                media.progress = progress;
                invalidateMediaCell(row);
            }
            @Override
            public void onDone(String mp4Path, int width, int height, int durationSec) {
                converters.remove(media);
                media.isVideo = true;
                media.localPath = mp4Path;
                if (width > 0) media.width = width;
                if (height > 0) media.height = height;
                media.duration = durationSec;
                media.orientation = 0;
                media.invert = 0;
                media.progress = 0f;
                refreshMediaCell(row);
                startMediaUpload(row, media, mp4Path, true, media.width, media.height, durationSec);
            }
            @Override
            public void onError() {
                converters.remove(media);
                media.state = MediaUploadState.STATE_ERROR;
                removeMediaFromRow(row, media);
                delegate.onContentChanged();
            }
        });
        converters.put(media, converter);
        converter.start();
    }

    private void startMediaUpload(BlockRow row, MediaUploadState media, String path, boolean isVideo, int w, int h, int durationSec) {
        RichMediaUploader existing = uploaders.remove(media);
        if (existing != null) existing.cancel();
        RichMediaUploader uploader = new RichMediaUploader(currentAccount, path, isVideo, w, h, durationSec, new RichMediaUploader.Listener() {
            @Override
            public void onWidthHeightResolved(int rw, int rh) {
                if (rw > 0 && rh > 0) {
                    media.width = rw;
                    media.height = rh;
                }
                invalidateMediaCell(row);
            }
            @Override
            public void onProgress(float progress) {
                media.progress = progress;
                invalidateMediaCell(row);
                delegate.onContentChanged();
            }
            @Override
            public void onPhotoUploaded(TLRPC.Photo photo) {
                media.photo = photo;
                media.state = MediaUploadState.STATE_DONE;
                final TLRPC.PhotoSize big = FileLoader.getClosestPhotoSizeWithSize(photo.sizes, AndroidUtilities.getPhotoSize());
                if (big != null && big.w > 0 && big.h > 0) {
                    media.width = big.w;
                    media.height = big.h;
                }
                final TL_iv.PageBlock item = itemBlockFor(row, media);
                if (item instanceof TL_iv.pageBlockPhoto) {
                    ((TL_iv.pageBlockPhoto) item).photo_id = photo.id;
                }
                uploaders.remove(media);
                refreshMediaCell(row);
                delegate.onContentChanged();
            }
            @Override
            public void onVideoUploaded(TLRPC.Document doc) {
                media.document = doc;
                media.state = MediaUploadState.STATE_DONE;
                final TL_iv.PageBlock item = itemBlockFor(row, media);
                if (item instanceof TL_iv.pageBlockVideo) {
                    ((TL_iv.pageBlockVideo) item).video_id = doc.id;
                }
                uploaders.remove(media);
                refreshMediaCell(row);
                delegate.onContentChanged();
            }
            @Override
            public void onError() {
                media.state = MediaUploadState.STATE_ERROR;
                uploaders.remove(media);
                removeMediaFromRow(row, media);
                delegate.onContentChanged();
            }
        });
        uploaders.put(media, uploader);
        uploader.start();
    }

    private void cancelMediaUpload(BlockRow row, MediaUploadState media) {
        if (media == null) { cancelMediaUpload(row); return; }
        RichMediaUploader u = uploaders.remove(media);
        if (u != null) u.cancel();
        RichMediaConverter c = converters.remove(media);
        if (c != null) c.cancel();
        if (history != null) history.flush();
        removeMediaFromRow(row, media);
        if (history != null) history.record();
        delegate.onContentChanged();
    }

    private void cancelMediaUpload(BlockRow row) {
        for (MediaUploadState ms : mediasOf(row)) {
            RichMediaUploader u = uploaders.remove(ms);
            if (u != null) u.cancel();
            RichMediaConverter c = converters.remove(ms);
            if (c != null) c.cancel();
        }
        int idx = rows.indexOf(row);
        if (idx >= 0) {
            if (history != null) history.flush();
            rows.remove(idx);
            adapter.update(true);
            if (history != null) history.record();
        }
        delegate.onContentChanged();
    }

    private void removeMediaFromRow(BlockRow row, MediaUploadState media) {
        if (row == null) return;
        if (isGallery(row.block)) {
            final ArrayList<TL_iv.PageBlock> items = galleryItems(row.block);
            final int idx = row.medias != null ? row.medias.indexOf(media) : -1;
            if (idx >= 0) {
                row.medias.remove(idx);
                if (items != null && idx < items.size()) items.remove(idx);
            }
            if (row.medias.isEmpty()) {
                removeRow(row);
            } else if (row.medias.size() == 1) {
                final TL_iv.PageCaption cap = row.block.caption;
                row.block = items.get(0);
                row.block.caption = cap;
                row.media = row.medias.get(0);
                row.medias = null;
                adapter.update(true);
                refreshMediaCell(row);
            } else {
                adapter.update(true);
                refreshMediaCell(row);
            }
        } else {
            removeRow(row);
        }
    }

    private void removeRow(BlockRow row) {
        final int idx = rows.indexOf(row);
        if (idx >= 0) {
            rows.remove(idx);
            adapter.update(true);
        }
    }

    private void switchGalleryMode(BlockRow row) {
        if (row == null || !isGallery(row.block)) return;
        if (history != null) history.flush();
        final ArrayList<TL_iv.PageBlock> items = galleryItems(row.block);
        final TL_iv.PageCaption caption = row.block.caption;
        final TL_iv.PageBlock swapped;
        if (row.block instanceof TL_iv.pageBlockSlideshow) {
            final TL_iv.pageBlockCollage c = new TL_iv.pageBlockCollage();
            c.items = items != null ? items : new ArrayList<>();
            c.caption = caption;
            swapped = c;
        } else {
            final TL_iv.pageBlockSlideshow s = new TL_iv.pageBlockSlideshow();
            s.items = items != null ? items : new ArrayList<>();
            s.caption = caption;
            swapped = s;
        }
        row.block = swapped;
        if (history != null) history.record();
        final View v = findViewByItemObject(row);
        if (v instanceof RichMediaCell) ((RichMediaCell) v).onModeChanged();
    }

    private void invalidateMediaCell(BlockRow row) {
        View v = findViewByItemObject(row);
        if (v instanceof RichMediaCell) {
            v.requestLayout();
            v.invalidate();
        }
    }

    private void refreshMediaCell(BlockRow row) {
        View v = findViewByItemObject(row);
        if (v instanceof RichMediaCell) ((RichMediaCell) v).refresh();
    }

    private void onChecklistToggle(BlockRow row, boolean checked) {
        if (row == null) return;
        row.checked = checked;
        if (history != null) {
            history.flush();
            history.record();
        }
    }

    private void addChecklistItem() {
        final BlockRow row = new BlockRow(new TL_iv.pageBlockParagraph(), 1, 0);
        row.checkbox = true;
        rows.add(row);
        adapter.update(false);
        if (history != null) history.record();
        post(() -> {
            View nv = findViewByItemObject(row);
            if (nv instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) nv;
                c.requestEditFocus();
                c.getEditText().setSelection(0);
            }
        });
    }

    private void onCaptionEnter(BlockRow row) {
        int idx = rows.indexOf(row);
        if (idx < 0) return;
        if (history != null) history.flush();
        final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
        para.quoteIds.addAll(row.quoteIds);
        rows.add(idx + 1, para);
        renumberAllRuns();
        adapter.update(false);
        if (history != null) history.record();
        post(() -> {
            View nv = findViewByItemObject(para);
            if (nv instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) nv;
                c.requestEditFocus();
                c.getEditText().setSelection(0);
            }
        });
    }

    private void onCellEnter(BlockRow row) {
        int idx = rows.indexOf(row);
        if (idx < 0) return;
        if (history != null) history.flush();

        CharSequence text;
        int caret;
        View v = findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            RichEditText et = ((RichTextCell) v).getEditText();
            text = et.getText();
            caret = et.getSelectionEnd();
        } else {
            text = RichTextCell.readStyledText(row.block);
            caret = text.length();
        }
        if (caret < 0 || caret > text.length()) caret = text.length();

        if (text.length() == 0) {
            if (!row.quoteIds.isEmpty()) {
                row.quoteIds.remove(row.quoteIds.size() - 1);
                renumberAllRuns();
                adapter.update(false);
                if (history != null) history.record();
                post(() -> focusRow(row));
                return;
            }
            if (row.level > 0) {
                cascadeOutdent(idx);
                renumberAllRuns();
                adapter.update(false);
                if (history != null) history.record();
                post(() -> focusRow(row));
                return;
            }
        }

        CharSequence head = text.subSequence(0, caret);
        CharSequence tail = text.subSequence(caret, text.length());

        boolean quoteRebind = false;
        if (row.block instanceof TL_iv.pageBlockBlockquote) {
            final long qid = RichContainer.newId();
            final TL_iv.RichText author = ((TL_iv.pageBlockBlockquote) row.block).caption;
            if (author != null && !(author instanceof TL_iv.textEmpty)) quoteAuthors.put(qid, author);
            row.quoteIds.add(qid);
            row.block = new TL_iv.pageBlockParagraph();
            quoteRebind = true;
        }
        RichTextCell.applyStyledTextToBlock(row.block, head);

        TL_iv.pageBlockParagraph next = new TL_iv.pageBlockParagraph();
        RichTextCell.applyStyledTextToBlock(next, tail);
        int nextNum = row.num > 0 ? row.num + 1 : row.num;
        BlockRow nextRow = new BlockRow(next, row.level, nextNum);
        nextRow.checkbox = row.checkbox;
        nextRow.quoteIds.addAll(row.quoteIds);
        rows.add(idx + 1, nextRow);
        renumberAllRuns();

        if (quoteRebind) {
            adapter.update(false);
            if (history != null) history.record();
            post(() -> focusRow(nextRow));
            return;
        }

        if (v instanceof RichTextCell) {
            ((RichTextCell) v).getEditText().deleteToEndSilently(caret);
        }
        adapter.updateWithoutNotify();
        final int insertPos = itemRows.indexOf(nextRow);
        if (insertPos < 0) {
            adapter.notifyDataSetChanged();
        } else {
            final RecyclerView.ItemAnimator animator = getItemAnimator();
            setItemAnimator(null);
            adapter.notifyItemInserted(insertPos);
            if (row.num > 0 && insertPos + 1 < itemRows.size()) {
                adapter.notifyItemRangeChanged(insertPos + 1, itemRows.size() - insertPos - 1);
            }
            post(() -> setItemAnimator(animator));
        }
        if (history != null) history.record();
        post(() -> {
            View nv = findViewByItemObject(nextRow);
            if (nv instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) nv;
                c.requestEditFocus();
                c.getEditText().setSelection(0);
            }
        });
    }

    private boolean isLastOfInnermostQuote(int idx) {
        final BlockRow row = rows.get(idx);
        if (row.quoteIds.isEmpty()) return false;
        final int d = row.quoteIds.size() - 1;
        final long innermost = row.quoteIds.get(d);
        if (idx + 1 >= rows.size()) return true;
        final BlockRow next = rows.get(idx + 1);
        return !(next.quoteIds.size() > d && next.quoteIds.get(d) == innermost);
    }

    private boolean isFirstOfInnermostQuote(int idx) {
        final BlockRow row = rows.get(idx);
        if (row.quoteIds.isEmpty()) return false;
        final int d = row.quoteIds.size() - 1;
        final long innermost = row.quoteIds.get(d);
        if (idx == 0) return true;
        final BlockRow prev = rows.get(idx - 1);
        return !(prev.quoteIds.size() > d && prev.quoteIds.get(d) == innermost);
    }

    private boolean collapseSingleBlockQuotes() {
        final HashMap<Long, Integer> count = new HashMap<>();
        for (int i = 0; i < rows.size(); i++) {
            final BlockRow r = rows.get(i);
            if (r.detailsEnd) continue;
            for (int q = 0; q < r.quoteIds.size(); q++) count.merge(r.quoteIds.get(q), 1, Integer::sum);
        }
        boolean changed = false;
        for (int i = 0; i < rows.size(); i++) {
            final BlockRow r = rows.get(i);
            if (r.quoteIds.size() != 1 || !(r.block instanceof TL_iv.pageBlockParagraph)) continue;
            final long qid = r.quoteIds.get(0);
            final Integer c = count.get(qid);
            if (c == null || c != 1) continue;
            final TL_iv.pageBlockBlockquote bq = new TL_iv.pageBlockBlockquote();
            bq.text = r.block.text != null ? r.block.text : new TL_iv.textEmpty();
            final TL_iv.RichText author = quoteAuthors.remove(qid);
            bq.caption = author != null ? author : new TL_iv.textEmpty();
            r.block = bq;
            r.quoteIds.clear();
            changed = true;
        }
        return changed;
    }

    private boolean onCellBackspaceAtStart(BlockRow row, boolean empty) {
        int idx = rows.indexOf(row);
        if (idx < 0) return false;
        if (demotesToParagraph(row.block)) {
            turnInto(row, new TL_iv.pageBlockParagraph(), 0, 0, false, false);
            return true;
        }
        if (row.level > 0) {
            if (history != null) history.flush();
            final int caret = captureCaret(row);
            cascadeOutdent(idx);
            renumberAllRuns();
            if (hasFocusedEdit()) {
                applyInPlaceUpdateKeepingFocus();
                if (history != null) history.record();
                restoreCaret(row, caret);
            } else {
                adapter.update(false);
                if (history != null) history.record();
                post(() -> focusRow(row));
            }
            return true;
        }
        if (!row.quoteIds.isEmpty() && empty
                && (isFirstOfInnermostQuote(idx) || !isLastOfInnermostQuote(idx))) {
            if (history != null) history.flush();
            row.quoteIds.remove(row.quoteIds.size() - 1);
            collapseSingleBlockQuotes();
            renumberAllRuns();
            adapter.update(false);
            if (history != null) history.record();
            post(() -> focusRow(row));
            return true;
        }
        if (idx <= 0) return false;
        BlockRow prev = rows.get(idx - 1);
        if (isDetailsHeader(prev) || prev.detailsEnd) {
            if (empty && !(isDetailsHeader(prev) && (idx + 1 >= rows.size() || rows.get(idx + 1).detailsEnd))) {
                if (history != null) history.flush();
                rows.remove(idx);
                renumberAllRuns();
                adapter.update(false);
                if (history != null) history.record();
                post(() -> focusRowAtEnd(prev));
            }
            return true;
        }
        if (isNonText(prev.block)) {
            if (history != null) history.flush();
            rows.remove(idx - 1);
            renumberAllRuns();
            adapter.update(false);
            if (history != null) history.record();
            post(() -> focusRow(row));
            return true;
        }
        if (history != null) history.flush();
        final View pv = findViewByItemObject(prev);
        final int removePos = itemRows.indexOf(row);
        if (pv instanceof RichTextCell && removePos >= 0) {
            final RichTextCell prevCell = (RichTextCell) pv;
            final RichEditText prevEt = prevCell.getEditText();
            final int targetOff = prevEt.length();
            prevEt.appendSilently(RichTextCell.readStyledText(row.block));
            RichTextCell.applyStyledTextToBlock(prev.block, prevEt.getText());
            final View cv = findViewByItemObject(row);
            if (cv instanceof RichTextCell) {
                ((RichTextCell) cv).getEditText().deleteToEndSilently(0);
            }
            prevCell.requestEditFocus();
            prevEt.setSelection(Math.max(0, Math.min(targetOff, prevEt.length())));
            rows.remove(idx);
            final boolean collapsed = collapseSingleBlockQuotes();
            renumberAllRuns();
            if (collapsed) {
                adapter.update(false);
                if (history != null) history.record();
                final BlockRow focusTarget = prev;
                final int caret = Math.max(0, targetOff);
                post(() -> {
                    final View pv2 = findViewByItemObject(focusTarget);
                    if (pv2 instanceof RichTextCell) {
                        final RichTextCell c = (RichTextCell) pv2;
                        c.requestEditFocus();
                        c.getEditText().setSelection(Math.max(0, Math.min(caret, c.getEditText().length())));
                    }
                });
                return true;
            }
            adapter.updateWithoutNotify();
            final RecyclerView.ItemAnimator animator = getItemAnimator();
            setItemAnimator(null);
            adapter.notifyItemRemoved(removePos);
            if ((prev.num > 0 || row.num > 0) && removePos < itemRows.size()) {
                adapter.notifyItemRangeChanged(removePos, itemRows.size() - removePos);
            }
            post(() -> setItemAnimator(animator));
            if (history != null) history.record();
            return true;
        }
        final CharSequence prevStyled = RichTextCell.readStyledText(prev.block);
        final int targetOff = prevStyled.length();
        final SpannableStringBuilder merged = new SpannableStringBuilder(prevStyled);
        merged.append(RichTextCell.readStyledText(row.block));
        RichTextCell.applyStyledTextToBlock(prev.block, merged);
        rows.remove(idx);
        collapseSingleBlockQuotes();
        renumberAllRuns();
        adapter.update(false);
        if (history != null) history.record();
        post(() -> {
            View pv2 = findViewByItemObject(prev);
            if (pv2 instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) pv2;
                c.requestEditFocus();
                int len = c.getEditText().length();
                c.getEditText().setSelection(Math.max(0, Math.min(targetOff, len)));
            }
        });
        return true;
    }

    private void onLanguageClick(BlockRow row, View btn) {
        if (row == null || !(row.block instanceof TL_iv.pageBlockPreformatted)) return;
        final Set<String> languagesSet = CodeHighlighting.getLanguages();
        if (languagesSet == null) return;
        final ArrayList<String> languages = new ArrayList<>(languagesSet);
        Collections.sort(languages);

        final TL_iv.pageBlockPreformatted pre = (TL_iv.pageBlockPreformatted) row.block;

        final ItemOptions o = delegate.makeMenu(btn);
        o.setScrimViewBackground(Theme.createRoundRectDrawable(dp(3), Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
        o.allowShowingOnTopOfKeyboard();
        o.setMaxHeight(dp(350));
        o.addChecked(TextUtils.isEmpty(pre.language), getString(R.string.ArticleNone), () -> updateLanguageOf(row, ""));
        if (!TextUtils.isEmpty(pre.language)) {
            o.addChecked(true, MessageObject.TextLayoutBlock.capitalizeLanguage(pre.language), null);
        }
        o.addGap();
        for (String lng : languages) {
            o.addChecked(
                TextUtils.equals(lng, pre.language),
                MessageObject.TextLayoutBlock.capitalizeLanguage(lng),
                () -> updateLanguageOf(row, lng)
            );
        }
        o.show();
    }

    private void updateLanguageOf(BlockRow row, String lng) {
        if (row == null || !(row.block instanceof TL_iv.pageBlockPreformatted)) return;
        final TL_iv.pageBlockPreformatted pre = (TL_iv.pageBlockPreformatted) row.block;
        if (TextUtils.equals(pre.language, lng)) return;
        if (history != null) history.flush();
        pre.language = lng;
        final RichTextCell cell = cellAt(rows.indexOf(row));
        if (cell != null) cell.updateLanguage();
        if (history != null) history.record();
    }

    private void focusRowAtEnd(BlockRow row) {
        View v = findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            RichTextCell c = (RichTextCell) v;
            c.requestEditFocus();
            RichEditText et = c.getEditText();
            et.setSelection(et.length());
        } else if (v instanceof RichDetailsCell) {
            RichDetailsCell c = (RichDetailsCell) v;
            c.requestEditFocus();
            RichEditText et = c.getEditText();
            et.setSelection(et.length());
        }
    }

    private void focusRowAtStart(BlockRow row) {
        View v = findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            RichTextCell c = (RichTextCell) v;
            c.requestEditFocus();
            c.getEditText().setSelection(0);
        } else if (v instanceof RichDetailsCell) {
            RichDetailsCell c = (RichDetailsCell) v;
            c.requestEditFocus();
            c.getEditText().setSelection(0);
        }
    }

    boolean onCellIndent(BlockRow row, boolean outdent) {
        int idx = rows.indexOf(row);
        if (idx < 0) return false;
        int caret = captureCaret(row);
        if (history != null) history.flush();
        if (!indentRow(idx, outdent, false)) return false;
        renumberAllRuns();
        if (hasFocusedEdit()) applyInPlaceUpdateKeepingFocus(); else adapter.update(false);
        if (history != null) history.record();
        restoreCaret(row, caret);
        return true;
    }

    private int[] selectedRowRange() {
        if (textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            final int s = rows.indexOf(rowForCell(textSelectionHelper.getStartCell()));
            final int e = rows.indexOf(rowForCell(textSelectionHelper.getEndCell()));
            if (s < 0 || e < 0) return null;
            return new int[] { Math.min(s, e), Math.max(s, e) };
        }
        final BlockRow f = findFocusedRow();
        final int i = f != null ? rows.indexOf(f) : -1;
        return i >= 0 ? new int[] { i, i } : null;
    }

    private boolean isRangeQuoted(int from, int to) {
        if (from < 0 || to >= rows.size() || from > to) return false;
        boolean any = false;
        for (int i = from; i <= to; i++) {
            final BlockRow r = rows.get(i);
            if (r.detailsEnd || isDetailsHeader(r)) continue;
            any = true;
            if (r.quoteIds.isEmpty() && !RichTextCell.isQuoteBlock(r.block)) return false;
        }
        return any;
    }

    public boolean isSelectionQuoted() {
        final int[] r = selectedRowRange();
        return r != null && isRangeQuoted(r[0], r[1]);
    }

    public void toggleQuoteOnSelection() {
        final int[] range = selectedRowRange();
        if (range == null) return;
        final int from = range[0], to = range[1];
        if (from < 0 || to >= rows.size() || from > to) return;
        if (history != null) history.flush();
        if (isRangeQuoted(from, to)) {
            for (int i = from; i <= to; i++) {
                final BlockRow r = rows.get(i);
                if (r.detailsEnd || isDetailsHeader(r)) continue;
                if (RichTextCell.isQuoteBlock(r.block)) {
                    final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                    p.text = r.block.text;
                    r.block = p;
                } else if (!r.quoteIds.isEmpty()) {
                    r.quoteIds.remove(r.quoteIds.size() - 1);
                }
            }
        } else {
            final long qid = RichContainer.newId();
            for (int i = from; i <= to; i++) {
                final BlockRow r = rows.get(i);
                if (r.detailsEnd || isDetailsHeader(r)) continue;
                if (RichTextCell.isQuoteBlock(r.block)) {
                    final long inner = RichContainer.newId();
                    final TL_iv.RichText author = RichTextCell.extractCaption(r.block);
                    if (author != null && !(author instanceof TL_iv.textEmpty)) quoteAuthors.put(inner, author);
                    r.quoteIds.add(inner);
                    final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                    p.text = r.block.text;
                    r.block = p;
                }
                r.quoteIds.add(0, qid);
            }
        }
        collapseSingleBlockQuotes();
        normalizeNestedQuotes();
        renumberAllRuns();
        final boolean wasSelecting = textSelectionHelper != null && textSelectionHelper.isInSelectionMode();
        final BlockRow fromRow = rows.get(Math.max(0, Math.min(from, rows.size() - 1)));
        final BlockRow toRow = rows.get(Math.max(0, Math.min(to, rows.size() - 1)));
        adapter.update(false);
        if (history != null) history.record();
        if (wasSelecting) {
            post(() -> reselectRowRange(fromRow, toRow));
        } else {
            post(() -> focusRow(fromRow));
        }
    }

    private void reselectRowRange(BlockRow fromRow, BlockRow toRow) {
        if (textSelectionHelper == null || fromRow == null || toRow == null) return;
        final int startPos = itemRows.indexOf(fromRow);
        final int endPos = itemRows.indexOf(toRow);
        if (startPos < 0 || endPos < 0) return;
        for (int i = 0; i < itemRows.size(); i++) {
            final BlockRow r = itemRows.get(i);
            if (r.authorQuoteId != 0) {
                textSelectionHelper.cacheText(i, RichTextStyle.plainOf(quoteAuthors.get(r.authorQuoteId)), null);
            } else {
                textSelectionHelper.cacheText(i, RichTextCell.readPlainText(r.block), null);
            }
        }
        textSelectionHelper.selectAllBlocksRange(Math.min(startPos, endPos), Math.max(startPos, endPos));
    }

    private BlockRow turnIntoTarget() {
        BlockRow row = findFocusedRow();
        if (row == null && textSelectionHelper != null && textSelectionHelper.isInSelectionMode()) {
            final int sCell = textSelectionHelper.getStartCell();
            final int eCell = textSelectionHelper.getEndCell();
            if (sCell == eCell) row = rowForCell(sCell);
        }
        return row;
    }

    void turnInto(BlockRow row, TL_iv.PageBlock newBlock, int level, int num, boolean checkbox, boolean checked) {
        if (row == null) row = turnIntoTarget();
        if (row == null || newBlock == null || rows.indexOf(row) < 0) return;
        if (isDetailsHeader(row) || row.detailsEnd) return;
        if (newBlock != row.block && isFormattable(row.block) && isFormattable(newBlock)) {
            final RichTextCell cell = cellAt(rows.indexOf(row));
            final CharSequence styled = cell != null ? cell.getEditText().getText() : RichTextCell.readStyledText(row.block);
            RichTextCell.applyStyledTextToBlock(newBlock, styled);
            final TL_iv.RichText caption = RichTextCell.extractCaption(row.block);
            if (caption != null && RichTextCell.extractCaption(newBlock) != null) {
                RichTextCell.setCaption(newBlock, caption);
            }
        }
        transformRow(row, newBlock, level, num, checkbox, checked);
    }

    void turnIntoKeepList(BlockRow row, TL_iv.PageBlock newBlock) {
        if (row == null) row = turnIntoTarget();
        if (row == null) return;
        turnInto(row, newBlock, row.level, row.num, row.checkbox, row.checked);
    }

    void turnIntoList(BlockRow row, int kind) {
        if (row == null) row = turnIntoTarget();
        if (row == null) return;
        if (kind == 0) {
            turnInto(row, row.block, 0, 0, false, false);
            return;
        }
        final int level = Math.max(1, row.level);
        final int num = kind == 2 ? Math.max(1, row.num) : 0;
        final boolean checkbox = kind == 3;
        final TL_iv.PageBlock block = row.block instanceof TL_iv.pageBlockParagraph ? row.block : new TL_iv.pageBlockParagraph();
        turnInto(row, block, level, num, checkbox, checkbox && row.checked);
    }

    static TL_iv.pageBlockBlockquote newBlockquote() {
        final TL_iv.pageBlockBlockquote q = new TL_iv.pageBlockBlockquote();
        q.caption = new TL_iv.textEmpty();
        return q;
    }

    static TL_iv.pageBlockPullquote newPullquote() {
        final TL_iv.pageBlockPullquote q = new TL_iv.pageBlockPullquote();
        q.caption = new TL_iv.textEmpty();
        return q;
    }

    void applyQuote(BlockRow row) {
        if (row == null) row = turnIntoTarget();
        if (row == null || rows.indexOf(row) < 0 || isDetailsHeader(row) || row.detailsEnd) return;
        if (history != null) history.flush();
        final boolean freshQuote = row.quoteIds.isEmpty() && !RichTextCell.isQuoteBlock(row.block);
        if (freshQuote) {
            row.block = newBlockquote();
        } else {
            if (RichTextCell.isQuoteBlock(row.block)) {
                final long qid = RichContainer.newId();
                final TL_iv.RichText author = RichTextCell.extractCaption(row.block);
                if (author != null && !(author instanceof TL_iv.textEmpty)) quoteAuthors.put(qid, author);
                row.quoteIds.add(qid);
            }
            row.block = new TL_iv.pageBlockParagraph();
            row.quoteIds.add(RichContainer.newId());
        }
        renumberAllRuns();
        if (freshQuote && hasFocusedEdit()) {
            applyInPlaceUpdateKeepingFocus();
            if (history != null) history.record();
            focusRow(row);
            return;
        }
        adapter.update(false);
        if (history != null) history.record();
        final BlockRow r = row;
        post(() -> focusRow(r));
    }

    private boolean normalizeNestedQuotes() {
        boolean changed = false;
        for (int i = 0; i < rows.size(); i++) {
            final BlockRow r = rows.get(i);
            if (r.quoteIds.isEmpty() || !RichTextCell.isQuoteBlock(r.block)) continue;
            final long qid = RichContainer.newId();
            final TL_iv.RichText author = RichTextCell.extractCaption(r.block);
            if (author != null && !(author instanceof TL_iv.textEmpty)) quoteAuthors.put(qid, author);
            r.quoteIds.add(qid);
            final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
            p.text = r.block.text;
            r.block = p;
            changed = true;
        }
        return changed;
    }

    private void transformRow(BlockRow row, TL_iv.PageBlock newBlock, int newLevel, int newNum, boolean checkbox, boolean checked) {
        int idx = rows.indexOf(row);
        if (idx < 0) return;
        if (history != null) history.flush();
        row.block = newBlock;
        row.level = newLevel;
        row.num = newNum;
        row.checkbox = checkbox;
        row.checked = checked;
        renumberAllRuns();
        BlockRow toFocus;
        if (newBlock instanceof TL_iv.pageBlockTable) {
            if (idx + 1 >= rows.size() || isNonText(rows.get(idx + 1).block)) {
                rows.add(idx + 1, new BlockRow(new TL_iv.pageBlockParagraph()));
            }
            toFocus = row;
        } else if (isNonText(newBlock)) {
            if (isMedia(newBlock) && row.media == null) {
                row.media = new MediaUploadState();
            }
            if (idx + 1 < rows.size() && !isNonText(rows.get(idx + 1).block)) {
                toFocus = rows.get(idx + 1);
            } else {
                BlockRow next = new BlockRow(new TL_iv.pageBlockParagraph());
                rows.add(idx + 1, next);
                toFocus = next;
            }
        } else {
            toFocus = row;
        }
        final boolean textInPlace = toFocus == row
            && !(newBlock instanceof TL_iv.pageBlockTable)
            && !isNonText(newBlock)
            && hasFocusedEdit();
        if (textInPlace) {
            applyInPlaceUpdateKeepingFocus();
            if (history != null) history.record();
            final View v = findViewByItemObject(row);
            if (v instanceof RichTextCell) {
                final RichTextCell c = (RichTextCell) v;
                c.requestEditFocus();
                c.getEditText().setSelection(c.getEditText().length());
            }
            return;
        }
        adapter.update(false);
        if (history != null) history.record();
        final BlockRow target = toFocus;
        post(() -> {
            View v = findViewByItemObject(target);
            if (v instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) v;
                c.requestEditFocus();
                int len = c.getEditText().length();
                c.getEditText().setSelection(len);
            } else if (v instanceof RichTableCell) {
                RichTableCell rtc = (RichTableCell) v;
                if (rtc.getGrid().getChildCount() > 0) {
                    View first = rtc.getGrid().getChildAt(0);
                    if (first instanceof RichTableCellHost) {
                        ((RichTableCellHost) first).editText.requestEditFocus();
                    }
                }
            }
        });
    }

    private boolean rangeIndent(int sCell, int eCell, boolean outdent) {
        if (sCell < 0 || eCell < sCell || eCell >= rows.size()) return false;
        if (history != null) history.flush();
        boolean anyChanged = false;
        if (outdent) {
            for (int i = eCell; i >= sCell; i--) {
                if (indentRow(i, true, true)) anyChanged = true;
            }
        } else {
            BlockRow first = rows.get(sCell);
            if (first.level >= 1) {
                if (sCell == 0 || rows.get(sCell - 1).level < first.level) {
                    return false;
                }
            }
            for (int i = sCell; i <= eCell; i++) {
                if (indentRow(i, false, true)) anyChanged = true;
            }
        }
        if (anyChanged) {
            renumberAllRuns();
            if (hasFocusedEdit()) applyInPlaceUpdateKeepingFocus(); else adapter.update(false);
            if (history != null) history.record();
        }
        return anyChanged;
    }

    boolean canIndentRow(int idx) {
        if (idx < 0 || idx >= rows.size()) return false;
        BlockRow row = rows.get(idx);
        final boolean structOk = row.level == 0
            ? (isListableText(row.block) || canNonTextJoinList(idx))
            : (idx > 0 && rows.get(idx - 1).level >= row.level);
        return structOk && indentKeepsDepth(idx);
    }

    private boolean canNonTextJoinList(int idx) {
        return idx > 0 && idx < rows.size()
            && isNonText(rows.get(idx).block)
            && rows.get(idx - 1).level >= 1;
    }

    BlockRow currentBlockRow() {
        final BlockRow focused = findFocusedRow();
        if (focused != null) return focused;
        if (textSelectionHelper != null) {
            final int s = textSelectionHelper.getStartCell();
            if (s >= 0) {
                final BlockRow r = rowForCell(s);
                if (r != null) return r;
            }
        }
        return null;
    }

    boolean canIndentTarget(BlockRow row) {
        if (row == null) return false;
        final int idx = rows.indexOf(row);
        return idx >= 0 && canIndentRow(idx);
    }

    boolean canOutdentTarget(BlockRow row) {
        if (row == null) return false;
        final int idx = rows.indexOf(row);
        return idx >= 0 && row.level > 0 && canOutdentRow(idx);
    }

    private int[] selectionRowRange() {
        if (textSelectionHelper == null) return null;
        final int s = textSelectionHelper.getStartCell();
        final int e = textSelectionHelper.getEndCell();
        if (s < 0 || e < 0) return null;
        final BlockRow sr = rowForCell(s);
        final BlockRow er = rowForCell(e);
        if (sr == null || er == null) return null;
        int si = rows.indexOf(sr);
        int ei = rows.indexOf(er);
        if (si < 0 || ei < 0) return null;
        if (si > ei) { final int t = si; si = ei; ei = t; }
        return new int[] { si, ei };
    }

    boolean indentSelection(boolean outdent) {
        final int[] range = selectionRowRange();
        if (range == null || range[0] == range[1]) {
            final BlockRow row = range == null ? currentBlockRow() : rows.get(range[0]);
            return row != null && onCellIndent(row, outdent);
        }
        return rangeIndent(range[0], range[1], outdent);
    }

    boolean canIndentSelection() {
        final int[] range = selectionRowRange();
        if (range == null) return canIndentTarget(currentBlockRow());
        for (int i = range[0]; i <= range[1]; i++) {
            if (canIndentTarget(rows.get(i))) return true;
        }
        return false;
    }

    boolean canOutdentSelection() {
        final int[] range = selectionRowRange();
        if (range == null) return canOutdentTarget(currentBlockRow());
        for (int i = range[0]; i <= range[1]; i++) {
            if (canOutdentTarget(rows.get(i))) return true;
        }
        return false;
    }

    boolean canOutdentRow(int idx) {
        if (idx < 0 || idx >= rows.size()) return false;
        return rows.get(idx).level > 0;
    }

    private boolean indentRow(int idx, boolean outdent, boolean skipOrphanCheck) {
        if (idx < 0 || idx >= rows.size()) return false;
        BlockRow row = rows.get(idx);
        if (outdent) {
            if (row.level <= 0) return false;
            cascadeOutdent(idx);
            return true;
        }
        if (row.level == 0) {
            final boolean listable = isListableText(row.block);
            if (!listable && !canNonTextJoinList(idx)) return false;
            if (!indentKeepsDepth(idx)) return false;
            final BlockRow prev = idx > 0 ? rows.get(idx - 1) : null;
            if (listable) {
                row.level = 1;
                row.num = (prev != null && prev.num > 0) ? 1 : 0;
            } else {
                row.level = prev.level;
                row.num = prev.num > 0 ? 1 : 0;
                row.checkbox = false;
                row.checked = false;
            }
            return true;
        }
        if (!skipOrphanCheck) {
            if (idx == 0) return false;
            if (rows.get(idx - 1).level < row.level) return false;
        }
        if (!indentKeepsDepth(idx)) return false;
        row.level++;
        return true;
    }

    private void cascadeOutdent(int idx) {
        BlockRow row = rows.get(idx);
        int oldLevel = row.level;
        if (oldLevel <= 0) return;
        int newLevel = oldLevel - 1;
        row.level = newLevel;
        if (newLevel == 0) {
            row.num = 0;
            row.checkbox = false;
            row.checked = false;
        }
        for (int i = idx + 1; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (r.level <= oldLevel) break;
            r.level--;
        }
    }

    private void renumberAllRuns() {
        for (int i = 0; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (r.level <= 0 || r.num <= 0) continue;
            if (isNonText(r.block)) continue;
            int L = r.level;
            int counter = 1;
            for (int j = i - 1; j >= 0; j--) {
                BlockRow p = rows.get(j);
                if (p.level < L) break;
                if (p.level == L) {
                    if (isNonText(p.block)) continue;
                    if (p.num <= 0) break;
                    counter++;
                }
            }
            r.num = counter;
        }
    }

    private int captureCaret(BlockRow row) {
        View v = findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            RichTextCell c = (RichTextCell) v;
            if (c.getEditText().isFocused()) {
                return c.getEditText().getSelectionEnd();
            }
        }
        return -1;
    }

    private void restoreCaret(BlockRow row, int caret) {
        if (caret < 0) return;
        post(() -> {
            View v = findViewByItemObject(row);
            if (v instanceof RichTextCell) {
                RichTextCell c = (RichTextCell) v;
                c.requestEditFocus();
                int len = c.getEditText().length();
                c.getEditText().setSelection(Math.max(0, Math.min(caret, len)));
            }
        });
    }

    private void focusRow(BlockRow row) {
        View v = findViewByItemObject(row);
        if (v instanceof RichTextCell) {
            ((RichTextCell) v).requestEditFocus();
        } else if (v instanceof RichDetailsCell) {
            ((RichDetailsCell) v).requestEditFocus();
        }
    }

    private boolean hasFocusedEdit() {
        return findFocus() instanceof RichEditText;
    }

    private void applyInPlaceUpdateKeepingFocus() {
        adapter.updateWithoutNotify();
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (child instanceof RichTextCell) {
                ((RichTextCell) child).rebindInPlace();
            }
        }
    }

    private static boolean isListableText(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockParagraph
            || isHeading(b)
            || b instanceof TL_iv.pageBlockPreformatted
            || b instanceof TL_iv.pageBlockFooter;
    }

    private static boolean isNonText(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockDivider
            || b instanceof TL_iv.pageBlockPhoto
            || b instanceof TL_iv.pageBlockVideo
            || b instanceof TL_iv.pageBlockCollage
            || b instanceof TL_iv.pageBlockSlideshow
            || b instanceof TL_iv.pageBlockAudio
            || b instanceof TL_iv.pageBlockMath
            || b instanceof TL_iv.pageBlockMap
            || b instanceof TL_iv.pageBlockTable
            || b instanceof TL_iv.pageBlockList
            || b instanceof TL_iv.pageBlockOrderedList;
    }

    TL_iv.RichMessage buildDraftRichMessage() {
        if (!hasAnyText()) return null;
        ArrayList<TL_iv.PageBlock> blocks = flattenRowsToBlocks();
        if (blocks.isEmpty()) return null;
        TL_iv.RichMessage rich = new TL_iv.RichMessage();
        rich.blocks = blocks;
        rich.photos = collectPhotos();
        rich.documents = collectDocuments();
        return rich;
    }

    public int findExceededLimit() {
        final AppGlobalConfig config = MessagesController.getInstance(currentAccount).config;
        int mediaCount = 0;
        for (int i = 0; i < rows.size(); i++) {
            mediaCount += mediasOf(rows.get(i)).size();
        }
        return RichEditorLimits.measure(flattenRowsToBlocks(), mediaCount).findExceeded(config);
    }

    private static final int INDENT_TEXT_DEPTH_RESERVE = 6;

    private boolean indentKeepsDepth(int idx) {
        final AppGlobalConfig config = MessagesController.getInstance(currentAccount).config;
        final int newLevel = rows.get(idx).level + 1;
        return newLevel <= config.richMessageMaxDepth.get() - INDENT_TEXT_DEPTH_RESERVE;
    }

    public boolean isWithinLimits() {
        return findExceededLimit() == RichEditorLimits.LIMIT_NONE;
    }

    boolean hasAnyText() {
        for (int i = 0; i < rows.size(); i++) {
            BlockRow r = rows.get(i);
            if (!RichTextCell.readPlainText(r.block).isEmpty()) return true;
            if (isMedia(r.block) || r.block instanceof TL_iv.pageBlockAudio) {
                for (MediaUploadState ms : mediasOf(r)) {
                    if (ms.isReady() || ms.isPending()) return true;
                }
            }
            if (r.block instanceof TL_iv.pageBlockMath && !android.text.TextUtils.isEmpty(((TL_iv.pageBlockMath) r.block).source)) return true;
            if (r.block instanceof TL_iv.pageBlockMap && RichMapCell.hasGeo((TL_iv.pageBlockMap) r.block)) return true;
            if (r.block instanceof TL_iv.pageBlockTable && tableHasText((TL_iv.pageBlockTable) r.block)) return true;
        }
        return false;
    }

    private static boolean tableHasText(TL_iv.pageBlockTable t) {
        if (t.title != null && !TextUtils.isEmpty(RichTextStyle.plainOf(t.title))) return true;
        if (t.rows == null) return false;
        for (int i = 0; i < t.rows.size(); i++) {
            final TL_iv.pageTableRow row = t.rows.get(i);
            for (int j = 0; j < row.cells.size(); j++) {
                if (!TableModel.readPlainText(row.cells.get(j)).isEmpty()) return true;
            }
        }
        return false;
    }

    ArrayList<TLRPC.Photo> collectPhotos() {
        ArrayList<TLRPC.Photo> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            for (MediaUploadState ms : mediasOf(rows.get(i))) {
                if (ms.isReady() && ms.photo != null && seen.add(ms.photo.id)) {
                    out.add(ms.photo);
                }
            }
        }
        return out;
    }

    ArrayList<TLRPC.Document> collectDocuments() {
        ArrayList<TLRPC.Document> out = new ArrayList<>();
        HashSet<Long> seen = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) {
            for (MediaUploadState ms : mediasOf(rows.get(i))) {
                if (ms.isReady() && ms.document != null && seen.add(ms.document.id)) {
                    out.add(ms.document);
                }
            }
        }
        return out;
    }

    boolean hasPendingUploads() {
        for (int i = 0; i < rows.size(); i++) {
            for (MediaUploadState ms : mediasOf(rows.get(i))) {
                if (ms.isPending()) return true;
            }
        }
        return false;
    }

    ArrayList<TL_iv.PageBlock> flattenRowsToBlocks() {
        return flattenRange(0, rows.size(), false);
    }

    private static boolean isEmptyTextBlock(TL_iv.PageBlock b) {
        if (b instanceof TL_iv.pageBlockBlockquote || b instanceof TL_iv.pageBlockBlockquoteBlocks
            || b instanceof TL_iv.pageBlockPullquote) return false;
        return !isNonText(b) && !(b instanceof TL_iv.pageBlockDetails)
            && RichTextCell.readPlainText(b).isEmpty();
    }

    private ArrayList<TL_iv.PageBlock> flattenRange(int from, int to, boolean insideDetails) {
        return flattenRange(from, to, insideDetails, 0);
    }

    private ArrayList<TL_iv.PageBlock> flattenRange(int from, int to, boolean insideDetails, int quoteDepth) {
        ArrayList<TL_iv.PageBlock> out = new ArrayList<>();
        int i = from;
        while (i < to) {
            BlockRow r = rows.get(i);
            if (r.detailsEnd) {
                i++;
                continue;
            }
            if (r.quoteIds.size() > quoteDepth) {
                final long qid = r.quoteIds.get(quoteDepth);
                int end = i + 1;
                while (end < to) {
                    final BlockRow re = rows.get(end);
                    if (re.quoteIds.size() > quoteDepth && re.quoteIds.get(quoteDepth) == qid) end++;
                    else break;
                }
                final ArrayList<TL_iv.PageBlock> inner = flattenRange(i, end, true, quoteDepth + 1);
                final TL_iv.PageBlock quote = buildQuoteBlock(inner, qid);
                if (quote != null) out.add(quote);
                i = end;
                continue;
            }
            if (isDetailsHeader(r)) {
                final TL_iv.pageBlockDetails details = (TL_iv.pageBlockDetails) r.block;
                if (details.title == null) details.title = new TL_iv.textEmpty();
                final int end = matchingDetailsEnd(i);
                details.blocks = flattenRange(i + 1, Math.min(end, to), true, quoteDepth);
                out.add(details);
                i = end + 1;
                continue;
            }
            if (r.level <= 0) {
                emitLeafBlock(r, out);
                i++;
            } else {
                int[] end = new int[] { i };
                TL_iv.PageBlock list = buildListBlock(i, r.level, r.num > 0, end, to, quoteDepth);
                if (list != null) out.add(list);
                i = end[0];
                if (i <= 0) i = to;
            }
        }
        if (!insideDetails) {
            while (!out.isEmpty() && isEmptyTextBlock(out.get(0))) out.remove(0);
            while (!out.isEmpty() && isEmptyTextBlock(out.get(out.size() - 1))) out.remove(out.size() - 1);
        }
        return out;
    }

    private TL_iv.PageBlock buildQuoteBlock(ArrayList<TL_iv.PageBlock> inner, long qid) {
        if (inner == null || inner.isEmpty()) return null;
        final TL_iv.RichText author = quoteAuthor(qid);
        if (inner.size() == 1 && inner.get(0) instanceof TL_iv.pageBlockParagraph) {
            final TL_iv.pageBlockBlockquote bq = new TL_iv.pageBlockBlockquote();
            bq.text = inner.get(0).text != null ? inner.get(0).text : new TL_iv.textEmpty();
            bq.caption = author;
            return bq;
        }
        final TL_iv.pageBlockBlockquoteBlocks bb = new TL_iv.pageBlockBlockquoteBlocks();
        bb.blocks = inner;
        bb.caption = author;
        return bb;
    }

    private TL_iv.RichText quoteAuthor(long qid) {
        final TL_iv.RichText author = quoteAuthors.get(qid);
        return author != null ? author : new TL_iv.textEmpty();
    }

    private void emitLeafBlock(BlockRow r, ArrayList<TL_iv.PageBlock> out) {
        if (r.block instanceof TL_iv.pageBlockDivider) {
            out.add(r.block);
        } else if (r.block instanceof TL_iv.pageBlockPhoto) {
            if (r.media != null && r.media.isReady() && ((TL_iv.pageBlockPhoto) r.block).photo_id != 0) {
                if (r.block.caption == null) {
                    r.block.caption = new TL_iv.PageCaption();
                    r.block.caption.text = new TL_iv.textEmpty();
                    r.block.caption.credit = new TL_iv.textEmpty();
                }
                out.add(r.block);
            }
        } else if (r.block instanceof TL_iv.pageBlockVideo) {
            if (r.media != null && r.media.isReady() && ((TL_iv.pageBlockVideo) r.block).video_id != 0) {
                if (r.block.caption == null) {
                    r.block.caption = new TL_iv.PageCaption();
                    r.block.caption.text = new TL_iv.textEmpty();
                    r.block.caption.credit = new TL_iv.textEmpty();
                }
                out.add(r.block);
            }
        } else if (isGallery(r.block)) {
            final ArrayList<TL_iv.PageBlock> srcItems = galleryItems(r.block);
            final List<MediaUploadState> ms = mediasOf(r);
            final ArrayList<TL_iv.PageBlock> ready = new ArrayList<>();
            for (int k = 0; srcItems != null && k < srcItems.size() && k < ms.size(); k++) {
                final TL_iv.PageBlock item = srcItems.get(k);
                if (ms.get(k).isReady() && mediaIdOf(item) != 0) {
                    RichCaptionController.ensureCaption(item);
                    ready.add(item);
                }
            }
            if (ready.size() >= 2) {
                final TL_iv.PageBlock outGallery;
                if (r.block instanceof TL_iv.pageBlockSlideshow) {
                    final TL_iv.pageBlockSlideshow s = new TL_iv.pageBlockSlideshow();
                    s.items = ready;
                    s.caption = r.block.caption;
                    outGallery = s;
                } else {
                    final TL_iv.pageBlockCollage c = new TL_iv.pageBlockCollage();
                    c.items = ready;
                    c.caption = r.block.caption;
                    outGallery = c;
                }
                RichCaptionController.ensureCaption(outGallery);
                out.add(outGallery);
            } else if (ready.size() == 1) {
                out.add(ready.get(0));
            }
        } else if (r.block instanceof TL_iv.pageBlockAudio) {
            if (r.media != null && r.media.isReady() && ((TL_iv.pageBlockAudio) r.block).audio_id != 0) {
                if (r.block.caption == null) {
                    r.block.caption = new TL_iv.PageCaption();
                    r.block.caption.text = new TL_iv.textEmpty();
                    r.block.caption.credit = new TL_iv.textEmpty();
                }
                out.add(r.block);
            }
        } else if (r.block instanceof TL_iv.pageBlockMap) {
            TL_iv.pageBlockMap map = (TL_iv.pageBlockMap) r.block;
            if (RichMapCell.hasGeo(map)) {
                if (map.caption == null) {
                    map.caption = new TL_iv.PageCaption();
                    map.caption.text = new TL_iv.textEmpty();
                    map.caption.credit = new TL_iv.textEmpty();
                }
                out.add(map);
            }
        } else if (r.block instanceof TL_iv.pageBlockMath) {
            if (!android.text.TextUtils.isEmpty(((TL_iv.pageBlockMath) r.block).source)) {
                out.add(r.block);
            }
        } else if (r.block instanceof TL_iv.pageBlockTable) {
            TL_iv.pageBlockTable t = (TL_iv.pageBlockTable) r.block;
            TableModel.normalizeForSend(t);
            if (tableHasText(t)) out.add(t);
        } else {
            out.add(r.block);
        }
    }

    private TL_iv.PageBlock buildListBlock(int from, int targetLevel, boolean ordered, int[] endIdx, int limit, int quoteDepth) {
        TL_iv.pageBlockOrderedList orderedList = ordered ? new TL_iv.pageBlockOrderedList() : null;
        TL_iv.pageBlockList unorderedList = ordered ? null : new TL_iv.pageBlockList();
        int counter = 1;
        int i = from;
        while (i < limit) {
            BlockRow r = rows.get(i);
            if (r.quoteIds.size() > quoteDepth) break;
            if (r.level < targetLevel) break;
            if (r.level == targetLevel && (r.num > 0) != ordered) break;
            if (r.level > targetLevel) break;

            final boolean startIsText = !isNonText(r.block);
            TL_iv.RichText text = null;
            if (startIsText) {
                text = r.block == null ? null : r.block.text;
                if (text == null) text = new TL_iv.textEmpty();
            }
            int j = i + 1;
            final ArrayList<TL_iv.PageBlock> childBlocks = new ArrayList<>();
            if (!startIsText) {
                emitLeafBlock(r, childBlocks);
            }
            while (j < limit) {
                final BlockRow rj = rows.get(j);
                if (rj.quoteIds.size() > quoteDepth) break;
                if (rj.level < targetLevel) break;
                if (rj.level == targetLevel) {
                    if (!isNonText(rj.block)) break;
                    emitLeafBlock(rj, childBlocks);
                    j++;
                    continue;
                }
                final int subLevel = rj.level;
                final boolean subOrdered = rj.num > 0;
                final int[] subEnd = new int[] { j };
                final TL_iv.PageBlock sub = buildListBlock(j, subLevel, subOrdered, subEnd, limit, quoteDepth);
                if (sub != null) childBlocks.add(sub);
                if (subEnd[0] <= j) break;
                j = subEnd[0];
            }

            if (startIsText && childBlocks.isEmpty()) {
                if (ordered) {
                    TL_iv.TL_pageListOrderedItemText item = new TL_iv.TL_pageListOrderedItemText();
                    item.num = counter + ".";
                    item.text = text;
                    item.checkbox = r.checkbox;
                    item.checked = r.checked;
                    orderedList.items.add(item);
                } else {
                    TL_iv.TL_pageListItemText item = new TL_iv.TL_pageListItemText();
                    item.text = text;
                    item.checkbox = r.checkbox;
                    item.checked = r.checked;
                    unorderedList.items.add(item);
                }
                counter++;
            } else {
                final ArrayList<TL_iv.PageBlock> blocks = new ArrayList<>();
                if (startIsText) {
                    TL_iv.pageBlockParagraph para = new TL_iv.pageBlockParagraph();
                    para.text = text;
                    blocks.add(para);
                }
                blocks.addAll(childBlocks);
                if (!blocks.isEmpty()) {
                    if (ordered) {
                        TL_iv.TL_pageListOrderedItemBlocks item = new TL_iv.TL_pageListOrderedItemBlocks();
                        item.num = counter + ".";
                        item.blocks = blocks;
                        item.checkbox = r.checkbox;
                        item.checked = r.checked;
                        orderedList.items.add(item);
                    } else {
                        TL_iv.TL_pageListItemBlocks item = new TL_iv.TL_pageListItemBlocks();
                        item.blocks = blocks;
                        item.checkbox = r.checkbox;
                        item.checked = r.checked;
                        unorderedList.items.add(item);
                    }
                    counter++;
                }
            }
            i = j;
        }
        endIdx[0] = i;
        if (ordered) {
            return orderedList.items.isEmpty() ? null : orderedList;
        } else {
            return unorderedList.items.isEmpty() ? null : unorderedList;
        }
    }

    BlockRow findFocusedRow() {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c instanceof RichTextCell) {
                RichTextCell rc = (RichTextCell) c;
                if (rc.getEditText().isFocused() || (rc.isAuthorVisible() && rc.isAuthorFocused())) return rc.getRow();
            }
        }
        return null;
    }

    private static boolean isArrowKey(int code) {
        return code == KeyEvent.KEYCODE_DPAD_LEFT
            || code == KeyEvent.KEYCODE_DPAD_RIGHT
            || code == KeyEvent.KEYCODE_DPAD_UP
            || code == KeyEvent.KEYCODE_DPAD_DOWN;
    }

    private boolean tryPlainArrowAcrossCells(int code) {
        boolean down = code == KeyEvent.KEYCODE_DPAD_DOWN;
        RichTableCell titleTable = focusedTitleTable();
        if (titleTable != null) {
            int tIdx = rows.indexOf(titleTable.getRow());
            if (tIdx < 0) return false;
            if (down) {
                if (titleTable.focusFirstCell()) return true;
                int nextIdx = findNextNavigableRow(tIdx + 1, +1);
                if (nextIdx < 0) {
                    BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
                    rows.add(para);
                    adapter.update(false);
                    post(() -> focusRow(para));
                } else {
                    BlockRow next = rows.get(nextIdx);
                    post(() -> focusNavRow(next, false));
                }
                return true;
            }
            int prevIdx = findNextNavigableRow(tIdx - 1, -1);
            if (prevIdx < 0) return false;
            BlockRow prev = rows.get(prevIdx);
            post(() -> focusNavRow(prev, true));
            return true;
        }

        final RichQuoteAuthorCell authorCell = findFocusedAuthorCell();
        if (authorCell != null) {
            final int ii = itemRows.indexOf(authorCell.getRow());
            if (ii < 0) return false;
            final Layout al = authorCell.authorEditText.getLayout();
            final int aLine = al != null ? al.getLineForOffset(authorCell.authorEditText.getSelectionEnd()) : 0;
            if (down) {
                if (al != null && aLine < al.getLineCount() - 1) return false;
                final BlockRow next = nextNavItemRow(ii + 1, +1);
                if (next == null) {
                    final BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
                    rows.add(para);
                    adapter.update(false);
                    post(() -> focusRow(para));
                    return true;
                }
                post(() -> focusItemRow(next, false));
                return true;
            } else {
                if (al != null && aLine > 0) return false;
                final BlockRow prev = nextNavItemRow(ii - 1, -1);
                if (prev == null) return false;
                post(() -> focusItemRow(prev, true));
                return true;
            }
        }

        BlockRow focused = findFocusedNavRow();
        if (focused == null) return false;
        int idx = rows.indexOf(focused);
        if (idx < 0) return false;
        View v = findViewByItemObject(focused);
        if (v instanceof RichTextCell) {
            final RichTextCell cell = (RichTextCell) v;
            if (cell.isAuthorVisible()) {
                if (down && !cell.isAuthorFocused()) {
                    final Layout bl = cell.getEditText().getLayout();
                    if (bl == null || bl.getLineForOffset(cell.getEditText().getSelectionEnd()) >= bl.getLineCount() - 1) {
                        cell.focusAuthorFromBody();
                        return true;
                    }
                } else if (!down && cell.isAuthorFocused()) {
                    final Layout al = cell.getAuthorEditText().getLayout();
                    if (al == null || al.getLineForOffset(cell.getAuthorEditText().getSelectionEnd()) <= 0) {
                        cell.focusBodyFromAuthor();
                        return true;
                    }
                }
            }
        }
        RichEditText et = focusedNavEditText(v);
        if (et == null) return false;
        Layout layout = et.getLayout();
        if (layout == null) return false;
        int caret = et.getSelectionEnd();
        int line = layout.getLineForOffset(caret);
        if (down) {
            if (line < layout.getLineCount() - 1) return false;
            final BlockRow authorRow = adjacentAuthorRow(focused, +1);
            if (authorRow != null) { post(() -> focusItemRow(authorRow, true)); return true; }
            int nextIdx = findNextNavigableRow(idx + 1, +1);
            if (nextIdx < 0) {
                BlockRow para = new BlockRow(new TL_iv.pageBlockParagraph());
                rows.add(para);
                adapter.update(false);
                post(() -> focusRow(para));
            } else {
                BlockRow next = rows.get(nextIdx);
                post(() -> focusNavRow(next, false));
            }
            return true;
        } else {
            if (line > 0) return false;
            final BlockRow authorRow = adjacentAuthorRow(focused, -1);
            if (authorRow != null) { post(() -> focusItemRow(authorRow, true)); return true; }
            int prevIdx = findNextNavigableRow(idx - 1, -1);
            if (prevIdx < 0) return false;
            BlockRow prev = rows.get(prevIdx);
            post(() -> focusNavRow(prev, true));
            return true;
        }
    }

    private int findNextNavigableRow(int start, int step) {
        int i = start;
        while (i >= 0 && i < rows.size()) {
            final BlockRow r = rows.get(i);
            if (r.detailsEnd || isHiddenByCollapse(i)) { i += step; continue; }
            final TL_iv.PageBlock b = r.block;
            if (!isNonText(b) || hasCaption(b) || b instanceof TL_iv.pageBlockTable) return i;
            i += step;
        }
        return -1;
    }

    private boolean isHiddenByCollapse(int idx) {
        int depth = 0;
        int collapsedDepth = -1;
        for (int i = 0; i < idx; i++) {
            final BlockRow r = rows.get(i);
            if (isDetailsHeader(r)) {
                depth++;
                if (collapsedDepth == -1 && !((TL_iv.pageBlockDetails) r.block).open) collapsedDepth = depth;
            } else if (r.detailsEnd) {
                if (collapsedDepth != -1 && depth == collapsedDepth) collapsedDepth = -1;
                depth--;
            }
        }
        return collapsedDepth != -1;
    }

    private static boolean hasCaption(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockMap || b instanceof TL_iv.pageBlockAudio || isMedia(b);
    }

    private RichEditText navEditTextOf(View v) {
        if (v instanceof RichTextCell) return ((RichTextCell) v).getEditText();
        if (v instanceof RichCaptionHost) return ((RichCaptionHost) v).getCaptionEditText();
        if (v instanceof RichDetailsCell) return ((RichDetailsCell) v).getEditText();
        return null;
    }

    private RichEditText focusedNavEditText(View v) {
        if (v instanceof RichTextCell) {
            final RichTextCell c = (RichTextCell) v;
            if (c.isAuthorVisible() && c.isAuthorFocused()) return c.getAuthorEditText();
            return c.getEditText();
        }
        return navEditTextOf(v);
    }

    private RichTableCell focusedTitleTable() {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c instanceof RichTableCell && ((RichTableCell) c).getTitleEditText().isFocused()) {
                return (RichTableCell) c;
            }
        }
        return null;
    }

    private BlockRow findFocusedNavRow() {
        for (int i = 0; i < getChildCount(); i++) {
            View c = getChildAt(i);
            if (c instanceof RichTextCell) {
                final RichTextCell rc = (RichTextCell) c;
                if (rc.getEditText().isFocused() || (rc.isAuthorVisible() && rc.isAuthorFocused())) return rc.getRow();
            } else if (c instanceof RichCaptionHost) {
                if (((RichCaptionHost) c).getCaptionEditText().isFocused()) return ((RichCaptionHost) c).getRow();
            } else if (c instanceof RichDetailsCell) {
                if (((RichDetailsCell) c).getEditText().isFocused()) return ((RichDetailsCell) c).getRow();
            }
        }
        return null;
    }

    private RichQuoteAuthorCell findFocusedAuthorCell() {
        for (int i = 0; i < getChildCount(); i++) {
            final View c = getChildAt(i);
            if (c instanceof RichQuoteAuthorCell && ((RichQuoteAuthorCell) c).authorEditText.isFocused()) {
                return (RichQuoteAuthorCell) c;
            }
        }
        return null;
    }

    private BlockRow adjacentAuthorRow(BlockRow row, int dir) {
        final int ii = itemRows.indexOf(row);
        if (ii < 0) return null;
        final int j = ii + dir;
        if (j < 0 || j >= itemRows.size()) return null;
        final BlockRow r = itemRows.get(j);
        return r.authorQuoteId != 0 ? r : null;
    }

    private BlockRow nextNavItemRow(int start, int step) {
        int i = start;
        while (i >= 0 && i < itemRows.size()) {
            final BlockRow r = itemRows.get(i);
            if (r.detailsEnd) { i += step; continue; }
            if (r.authorQuoteId != 0) return r;
            final TL_iv.PageBlock b = r.block;
            if (!isNonText(b) || hasCaption(b) || b instanceof TL_iv.pageBlockTable) return r;
            i += step;
        }
        return null;
    }

    private void focusItemRow(BlockRow row, boolean atEnd) {
        final View v = findViewByItemObject(row);
        if (v instanceof RichQuoteAuthorCell) {
            final RichEditText et = ((RichQuoteAuthorCell) v).authorEditText;
            et.requestEditFocus();
            et.setSelection(atEnd ? et.length() : 0);
            return;
        }
        focusNavRow(row, atEnd);
    }

    private void focusNavRow(BlockRow row, boolean atEnd) {
        View v = findViewByItemObject(row);
        if (v instanceof RichTableCell) {
            ((RichTableCell) v).focusEdgeCell(atEnd);
            return;
        }
        if (atEnd && v instanceof RichTextCell && ((RichTextCell) v).isAuthorVisible()) {
            ((RichTextCell) v).focusAuthorEnd();
            return;
        }
        RichEditText et = navEditTextOf(v);
        if (et == null) { focusRow(row); return; }
        et.requestEditFocus();
        if (atEnd) et.setSelection(et.length());
    }

    private boolean tryEscalateSelectionFromCaret(int code) {
        BlockRow focused = findFocusedRow();
        if (focused == null) return false;
        int idx = rows.indexOf(focused);
        if (idx < 0) return false;
        View v = findViewByItemObject(focused);
        if (!(v instanceof RichTextCell)) return false;
        RichTextCell cell = (RichTextCell) v;
        RichEditText et = cell.getEditText();
        Layout layout = et.getLayout();
        if (layout == null) return false;
        int caret = et.getSelectionEnd();
        int textLen = et.length();

        boolean forward;
        switch (code) {
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (caret < textLen) return false;
                forward = true; break;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_DPAD_UP:
                if (caret > 0) return false;
                forward = false; break;
            default: return false;
        }

        int nextIdx = forward ? idx + 1 : idx - 1;
        if (nextIdx < 0 || nextIdx >= rows.size()) return false;
        BlockRow next = rows.get(nextIdx);

        for (int i = 0; i < rows.size(); i++) {
            textSelectionHelper.cacheText(i, RichTextCell.readPlainText(rows.get(i).block), null);
        }

        if (textLen == 0) return false;

        int anchor = Math.max(0, Math.min(et.getSelectionStart(), textLen));

        if (forward) {
            if (!textSelectionHelper.selectRangeOf(cell, anchor, anchor < textLen ? textLen : textLen - 1)) return false;
            View nv = findViewByItemObject(next);
            if (nv instanceof TextSelectionHelper.ArticleSelectableView) {
                textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) nv, 0);
            } else {
                final BlockRow target = next;
                scrollToPosition(nextIdx);
                post(() -> {
                    View t = findViewByItemObject(target);
                    if (t instanceof TextSelectionHelper.ArticleSelectableView) {
                        textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) t, 0);
                    }
                });
            }
        } else {
            if (!textSelectionHelper.selectRangeOf(cell, anchor, anchor > 0 ? 0 : 1)) return false;
            View nv = findViewByItemObject(next);
            if (nv instanceof TextSelectionHelper.ArticleSelectableView) {
                int prevLen = nv instanceof RichTextCell ? ((RichTextCell) nv).getEditText().length() : 0;
                textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) nv, prevLen);
            } else {
                final BlockRow target = next;
                scrollToPosition(nextIdx);
                post(() -> {
                    View t = findViewByItemObject(target);
                    if (t instanceof TextSelectionHelper.ArticleSelectableView) {
                        int len = t instanceof RichTextCell ? ((RichTextCell) t).getEditText().length() : 0;
                        textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) t, len);
                    }
                });
            }
        }
        return true;
    }

    private RichTableCell findTableCellAncestor(View v) {
        android.view.ViewParent p = v == null ? null : v.getParent();
        while (p != null) {
            if (p instanceof RichTableCell) return (RichTableCell) p;
            p = p.getParent();
        }
        return null;
    }

    private RichTextCell cellAt(int adapterPos) {
        if (adapterPos < 0) return null;
        View v = layoutManager.findViewByPosition(adapterPos);
        return v instanceof RichTextCell ? (RichTextCell) v : null;
    }

    private View selectableAt(int adapterPos) {
        if (adapterPos < 0) return null;
        return layoutManager.findViewByPosition(adapterPos);
    }

    private int prevTextOffset(int adapterPos) {
        View v = selectableAt(adapterPos);
        if (v instanceof RichTextCell) {
            Layout pl = ((RichTextCell) v).getEditText().getLayout();
            return pl != null ? pl.getText().length() : 0;
        }
        return 0;
    }

    private boolean tryExtendSelectionAcrossCells(int code, boolean byWord) {
        int anchorPos = textSelectionHelper.getAnchorCell();
        int anchorChild = textSelectionHelper.getAnchorChildPosition();
        int anchorOff = textSelectionHelper.getAnchorOffset();
        int sPos = textSelectionHelper.getStartCell();
        int sChild = textSelectionHelper.getStartChildPosition();
        int sOff = textSelectionHelper.getStartOffset();
        int ePos = textSelectionHelper.getEndCell();
        int eChild = textSelectionHelper.getEndChildPosition();
        int eOff = textSelectionHelper.getEndOffset();

        int curPos, curChild, curOff;
        if (anchorPos == sPos && anchorChild == sChild && anchorOff == sOff) {
            curPos = ePos; curChild = eChild; curOff = eOff;
        } else {
            curPos = sPos; curChild = sChild; curOff = sOff;
        }

        View curView = selectableAt(curPos);
        if (curView == null) return false;

        int newPos = curPos;
        int newChild = curChild;
        int newOff = curOff;

        if (curView instanceof RichTableCell) {
            RichTableCell rtc = (RichTableCell) curView;
            TableModel m = rtc.getModel();
            if (m == null || m.anchors().isEmpty()) return false;
            final int childCount = m.anchors().size() + 1;
            if (curChild < 0 || curChild >= childCount) curChild = 0;
            RichEditText curEt = rtc.editTextForChildPos(curChild);
            Layout layout = curEt != null ? curEt.getLayout() : null;
            if (layout == null) return false;
            CharSequence text = layout.getText();
            int textLen = text.length();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    newOff = byWord ? wordRight(text, curOff) : Math.min(textLen, curOff + 1);
                    if (newOff == curOff && curOff >= textLen) {
                        if (curChild + 1 < childCount) {
                            newChild = curChild + 1; newOff = 0;
                        } else if (curPos + 1 < rows.size()) {
                            newPos = curPos + 1; newChild = 0; newOff = 0;
                        }
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    newOff = byWord ? wordLeft(text, curOff) : Math.max(0, curOff - 1);
                    if (newOff == curOff && curOff <= 0) {
                        if (curChild - 1 >= 0) {
                            newChild = curChild - 1;
                            RichEditText prev = rtc.editTextForChildPos(newChild);
                            Layout pl = prev != null ? prev.getLayout() : null;
                            newOff = pl != null ? pl.getText().length() : 0;
                        } else if (curPos - 1 >= 0) {
                            newPos = curPos - 1; newChild = 0;
                            newOff = prevTextOffset(newPos);
                        }
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    int line = layout.getLineForOffset(curOff);
                    if (line + 1 < layout.getLineCount()) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line + 1, x);
                    } else if (curChild == 0) {
                        if (childCount > 1) { newChild = 1; newOff = 0; }
                        else if (curPos + 1 < rows.size()) { newPos = curPos + 1; newChild = 0; newOff = 0; }
                        else newOff = textLen;
                    } else {
                        int below = findTableAnchorBelow(m, curChild - 1);
                        if (below >= 0) {
                            newChild = below + 1; newOff = 0;
                        } else if (curPos + 1 < rows.size()) {
                            newPos = curPos + 1; newChild = 0; newOff = 0;
                        } else {
                            newOff = textLen;
                        }
                    }
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    int line = layout.getLineForOffset(curOff);
                    if (line - 1 >= 0) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line - 1, x);
                    } else if (curChild == 0) {
                        if (curPos - 1 >= 0) {
                            newPos = curPos - 1; newChild = 0;
                            newOff = prevTextOffset(newPos);
                        } else {
                            newOff = 0;
                        }
                    } else {
                        int above = findTableAnchorAbove(m, curChild - 1);
                        if (above >= 0) {
                            newChild = above + 1;
                            RichEditText prev = rtc.editTextForChildPos(newChild);
                            Layout pl = prev != null ? prev.getLayout() : null;
                            newOff = pl != null ? pl.getText().length() : 0;
                        } else {
                            newChild = 0;
                            newOff = rtc.getTitleEditText().length();
                        }
                    }
                    break;
                }
            }
        } else if (curView instanceof RichTextCell) {
            Layout layout = ((RichTextCell) curView).getEditText().getLayout();
            if (layout == null) return false;
            CharSequence text = layout.getText();
            int textLen = text.length();
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    newOff = byWord ? wordRight(text, curOff) : Math.min(textLen, curOff + 1);
                    if (newOff == curOff && curOff >= textLen && curPos + 1 < rows.size()) {
                        newPos = curPos + 1; newOff = 0;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    newOff = byWord ? wordLeft(text, curOff) : Math.max(0, curOff - 1);
                    if (newOff == curOff && curOff <= 0 && curPos - 1 >= 0) {
                        newPos = curPos - 1;
                        newOff = prevTextOffset(newPos);
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_DOWN: {
                    int line = layout.getLineForOffset(curOff);
                    if (line + 1 < layout.getLineCount()) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line + 1, x);
                    } else if (curPos + 1 < rows.size()) {
                        newPos = curPos + 1; newOff = 0;
                    } else {
                        newOff = textLen;
                    }
                    break;
                }
                case KeyEvent.KEYCODE_DPAD_UP: {
                    int line = layout.getLineForOffset(curOff);
                    if (line - 1 >= 0) {
                        float x = layout.getPrimaryHorizontal(curOff);
                        newOff = layout.getOffsetForHorizontal(line - 1, x);
                    } else if (curPos - 1 >= 0) {
                        newPos = curPos - 1;
                        newOff = prevTextOffset(newPos);
                    } else {
                        newOff = 0;
                    }
                    break;
                }
            }
        } else if (curView instanceof RichDividerCell || curView instanceof RichMediaCell
                || curView instanceof RichAudioCell || curView instanceof RichMapCell || curView instanceof RichMathCell) {
            switch (code) {
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    if (curPos + 1 < rows.size()) {
                        newPos = curPos + 1; newOff = 0;
                    }
                    break;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                case KeyEvent.KEYCODE_DPAD_UP:
                    if (curPos - 1 >= 0) {
                        newPos = curPos - 1;
                        newOff = prevTextOffset(newPos);
                    }
                    break;
            }
        } else {
            return false;
        }

        if (newPos == curPos && newChild == curChild && newOff == curOff) return true;

        if (newPos != curPos) {
            scrollToPosition(newPos);
        }
        final int targetPos = newPos;
        final int targetChild = newChild;
        final int targetOff = newOff;
        View targetView = selectableAt(targetPos);
        if (!(targetView instanceof TextSelectionHelper.ArticleSelectableView)) {
            post(() -> {
                View t = selectableAt(targetPos);
                if (t instanceof TextSelectionHelper.ArticleSelectableView) {
                    textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) t, targetChild, targetOff);
                }
            });
            return true;
        }
        return textSelectionHelper.extendSelectionTo((TextSelectionHelper.ArticleSelectableView) targetView, targetChild, targetOff);
    }

    private int findTableAnchorBelow(TableModel m, int curChild) {
        if (curChild < 0 || curChild >= m.anchors().size()) return -1;
        TL_iv.pageTableCell c = m.anchors().get(curChild);
        int aR = m.anchorRowOf(c);
        int aC = m.anchorColOf(c);
        int targetR = aR + Math.max(1, TableModel.spanRow(c));
        if (targetR >= m.rowCount) return -1;
        TL_iv.pageTableCell tCell = m.grid[targetR][Math.min(aC, m.colCount - 1)];
        return m.flatIndexOfAnchor(tCell);
    }

    private int findTableAnchorAbove(TableModel m, int curChild) {
        if (curChild < 0 || curChild >= m.anchors().size()) return -1;
        TL_iv.pageTableCell c = m.anchors().get(curChild);
        int aR = m.anchorRowOf(c);
        int aC = m.anchorColOf(c);
        int targetR = aR - 1;
        if (targetR < 0) return -1;
        TL_iv.pageTableCell tCell = m.grid[targetR][Math.min(aC, m.colCount - 1)];
        return m.flatIndexOfAnchor(tCell);
    }

    private static int wordRight(CharSequence text, int offset) {
        BreakIterator bi = BreakIterator.getWordInstance();
        bi.setText(text.toString());
        int next = bi.following(Math.min(offset, text.length()));
        return next == BreakIterator.DONE ? text.length() : next;
    }

    private static int wordLeft(CharSequence text, int offset) {
        BreakIterator bi = BreakIterator.getWordInstance();
        bi.setText(text.toString());
        int prev = bi.preceding(Math.max(0, Math.min(offset, text.length())));
        return prev == BreakIterator.DONE ? 0 : prev;
    }

    private boolean isWholeDocumentSelected() {
        if (rows.isEmpty() || !textSelectionHelper.isInSelectionMode()) return false;
        if (textSelectionHelper.getStartCell() != 0
                || textSelectionHelper.getStartChildPosition() != 0
                || textSelectionHelper.getStartOffset() > 0) {
            return false;
        }
        final int last = rows.size() - 1;
        if (textSelectionHelper.getEndCell() != last) return false;
        final BlockRow lastRow = rows.get(last);
        final String lastAuthor = RichTextCell.isQuoteBlock(lastRow.block) ? RichTextCell.readAuthorPlain(lastRow.block) : "";
        final int lastChildPos = lastAuthor.isEmpty() ? 0 : 1;
        if (textSelectionHelper.getEndChildPosition() != lastChildPos) return false;
        final int lastLen = lastChildPos == 1 ? lastAuthor.length() : RichTextCell.readPlainText(lastRow.block).length();
        return textSelectionHelper.getEndOffset() >= lastLen;
    }

    private boolean tryEscalateSelectAll() {
        if (rows.isEmpty()) return false;
        if (!textSelectionHelper.isInSelectionMode()) {
            if (selectCurrentUnit()) return true;
        } else if (tryEscalateWithinTable()) {
            return true;
        }
        for (int i = 0; i < itemRows.size(); i++) {
            final BlockRow r = itemRows.get(i);
            if (r.authorQuoteId != 0) {
                textSelectionHelper.cacheText(i, RichTextStyle.plainOf(quoteAuthors.get(r.authorQuoteId)), null);
                continue;
            }
            textSelectionHelper.cacheText(i, RichTextCell.readPlainText(r.block), null);
            if (RichTextCell.isQuoteBlock(r.block)) {
                final String author = RichTextCell.readAuthorPlain(r.block);
                if (!author.isEmpty()) textSelectionHelper.cacheChildText(i, 1, author);
            }
        }
        final int last = itemRows.size() - 1;
        final BlockRow lastRow = itemRows.get(last);
        final String lastAuthor = (lastRow.authorQuoteId == 0 && RichTextCell.isQuoteBlock(lastRow.block))
            ? RichTextCell.readAuthorPlain(lastRow.block) : "";
        if (!lastAuthor.isEmpty()) {
            textSelectionHelper.selectAllBlocksRange(0, last, 1, lastAuthor.length());
        } else {
            textSelectionHelper.selectAllBlocksRange(0, last);
        }
        return true;
    }

    private RichTextCell cellForEditText(RichEditText et) {
        ViewParent p = et.getParent();
        while (p != null) {
            if (p instanceof RichTextCell) return (RichTextCell) p;
            p = p.getParent();
        }
        return null;
    }

    private boolean selectCurrentUnit() {
        final RichEditText et = findFocusedEditText();
        if (et == null || et.getText() == null) return false;
        final int len = et.getText().length();
        if (len <= 0) return false;

        final RichTableCell rtc = findTableCellAncestor(et);
        if (rtc != null) {
            int childPos = -1;
            if (et == rtc.getTitleEditText()) {
                childPos = rtc.titleChildPos();
            } else {
                final RichTableCellHost host = rtc.findHostContaining(et);
                if (host != null) childPos = rtc.childPosForAnchor(host.cell);
            }
            if (childPos < 0) return false;
            et.setSelection(et.getSelectionEnd());
            return textSelectionHelper.selectRangeOf(rtc, childPos, 0, len);
        }

        final RichCaptionHost ch = findCaptionHostAncestor(et);
        if (ch instanceof TextSelectionHelper.ArticleSelectableView) {
            et.setSelection(et.getSelectionEnd());
            return textSelectionHelper.selectRangeOf((TextSelectionHelper.ArticleSelectableView) ch, 0, 0, len);
        }

        final RichTextCell cell = cellForEditText(et);
        if (cell != null) {
            final int childPos = et == cell.getAuthorEditText() ? 1 : 0;
            et.setSelection(et.getSelectionEnd());
            return textSelectionHelper.selectRangeOf(cell, childPos, 0, len);
        }
        return false;
    }

    private boolean tryEscalateWithinTable() {
        final int sPos = textSelectionHelper.getStartCell();
        if (sPos != textSelectionHelper.getEndCell()) return false;
        final View v = selectableAt(sPos);
        if (!(v instanceof RichTableCell)) return false;
        final RichTableCell rtc = (RichTableCell) v;
        if (isWholeTableSelected(rtc)) return false;
        return selectWholeTable(rtc);
    }

    private int[] wholeTableEnd(RichTableCell rtc) {
        for (int cp = rtc.childCount() - 1; cp >= 0; cp--) {
            final int l = rtc.childTextLength(cp);
            if (l > 0) return new int[] { cp, l };
        }
        return null;
    }

    private boolean selectWholeTable(RichTableCell rtc) {
        final int[] end = wholeTableEnd(rtc);
        if (end == null) return false;
        return textSelectionHelper.selectChildRange(rtc, 0, 0, end[0], end[1]);
    }

    private boolean isWholeTableSelected(RichTableCell rtc) {
        final int[] end = wholeTableEnd(rtc);
        if (end == null) return false;
        return textSelectionHelper.getStartChildPosition() == 0
            && textSelectionHelper.getStartOffset() == 0
            && textSelectionHelper.getEndChildPosition() == end[0]
            && textSelectionHelper.getEndOffset() == end[1];
    }

    private void copyHelperSelection() {
        CharSequence plain = textSelectionHelper.getSelectedTextPublic();
        if (plain == null || plain.length() == 0) return;
        writeSelectionToClipboard(plain);
    }

    private void cutHelperSelection() {
        CharSequence plain = textSelectionHelper.getSelectedTextPublic();
        if (plain != null && plain.length() > 0) {
            writeSelectionToClipboard(plain);
        }
        deleteHelperSelection();
    }

    private void writeSelectionToClipboard(CharSequence plain) {
        String html = buildSelectionHtml();
        try {
            ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            if (cm == null) return;
            ClipData clip = html != null
                ? ClipData.newHtmlText("label", plain, html)
                : ClipData.newPlainText("label", plain);
            cm.setPrimaryClip(clip);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private String buildSelectionHtml() {
        int a = rows.indexOf(rowForCell(textSelectionHelper.getStartCell()));
        int b = rows.indexOf(rowForCell(textSelectionHelper.getEndCell()));
        int sOff = textSelectionHelper.getStartOffset();
        int eOff = textSelectionHelper.getEndOffset();
        if (a < 0 || b < 0 || a >= rows.size() || b >= rows.size()) return null;
        if (a > b) { int t = a; a = b; b = t; int o = sOff; sOff = eOff; eOff = o; }
        else if (a == b && sOff > eOff) { int o = sOff; sOff = eOff; eOff = o; }
        try {
            String html = RichHtml.serialize(rows, a, b, sOff, eOff, quoteAuthors);
            if (TextUtils.isEmpty(html)) return null;
            RichMediaClipboard.set(collectMediaPhotos(a, b), collectMediaDocuments(a, b));
            return html;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private void pasteAtHelperSelection() {
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0) return;
        ClipData.Item item = clip.getItemAt(0);

        String html = null;
        if (clip.getDescription() != null && clip.getDescription().hasMimeType("text/html")) {
            try { html = item.getHtmlText(); } catch (Exception ignore) {}
        }
        if (!TextUtils.isEmpty(html)) {
            try {
                final HashMap<Long, TL_iv.RichText> authors = new HashMap<>();
                List<BlockRow> blocks = resolvePastedMedia(RichHtml.parse(html, authors));
                if (blocks != null && !blocks.isEmpty() && pasteBlocksAtSelection(blocks)) {
                    if (!authors.isEmpty()) quoteAuthors.putAll(authors);
                    return;
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }

        CharSequence pasted = item.coerceToText(getContext());
        if (pasted == null) pasted = "";

        int sCell = textSelectionHelper.getStartCell();
        int sOff = textSelectionHelper.getStartOffset();
        int eCell = textSelectionHelper.getEndCell();
        int eOff = textSelectionHelper.getEndOffset();

        String[] lines = pasted.toString().split("\n", -1);
        applyEditRange(sCell, sOff, eCell, eOff, lines);
    }

    private boolean pasteBlocksAtSelection(List<BlockRow> blocks) {
        int a = rows.indexOf(rowForCell(textSelectionHelper.getStartCell()));
        int b = rows.indexOf(rowForCell(textSelectionHelper.getEndCell()));
        return spliceBlocksInto(a, b, textSelectionHelper.getStartOffset(), textSelectionHelper.getEndOffset(), blocks);
    }

    private boolean onCellPaste(BlockRow row, RichEditText et) {
        if (row == null || et == null) return false;
        ClipboardManager cm = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return false;
        ClipData clip = cm.getPrimaryClip();
        if (clip == null || clip.getItemCount() == 0 || clip.getDescription() == null
            || !clip.getDescription().hasMimeType("text/html")) {
            return false;
        }
        String html;
        try { html = clip.getItemAt(0).getHtmlText(); } catch (Exception e) { return false; }
        if (TextUtils.isEmpty(html)) return false;
        List<BlockRow> blocks;
        final HashMap<Long, TL_iv.RichText> authors = new HashMap<>();
        try { blocks = resolvePastedMedia(RichHtml.parse(html, authors)); } catch (Exception e) { FileLog.e(e); return false; }
        if (blocks == null || blocks.isEmpty()) return false;
        if (blocks.size() == 1 && isPlainParagraphRow(blocks.get(0))) return false;
        int idx = rows.indexOf(row);
        if (idx < 0) return false;
        int s = Math.max(0, Math.min(et.getSelectionStart(), et.getSelectionEnd()));
        int e = Math.max(s, Math.max(et.getSelectionStart(), et.getSelectionEnd()));
        final boolean ok = spliceBlocksInto(idx, idx, s, e, blocks);
        if (ok && !authors.isEmpty()) quoteAuthors.putAll(authors);
        return ok;
    }

    private boolean spliceBlocksInto(int a, int b, int sOff, int eOff, List<BlockRow> blocks) {
        if (a < 0 || b < 0 || a >= rows.size() || b >= rows.size()) return false;
        if (a > b) { int t = a; a = b; b = t; int o = sOff; sOff = eOff; eOff = o; }
        else if (a == b && sOff > eOff) { int o = sOff; sOff = eOff; eOff = o; }

        for (int i = a; i <= b; i++) {
            BlockRow r = rows.get(i);
            if (r.detailsEnd || isDetailsHeader(r) || isNonText(r.block) || hasCaption(r.block)
                || r.block instanceof TL_iv.pageBlockTable) {
                return false;
            }
        }

        BlockRow startRow = rows.get(a);
        BlockRow endRow = rows.get(b);
        CharSequence startStyled = RichTextCell.readStyledText(startRow.block);
        CharSequence endStyled = a == b ? startStyled : RichTextCell.readStyledText(endRow.block);
        if (startStyled == null) startStyled = "";
        if (endStyled == null) endStyled = "";
        sOff = Math.max(0, Math.min(sOff, startStyled.length()));
        eOff = Math.max(0, Math.min(eOff, endStyled.length()));
        CharSequence head = startStyled.subSequence(0, sOff);
        CharSequence tail = endStyled.subSequence(eOff, endStyled.length());

        if (history != null) history.flush();

        BlockRow focusRow;
        int focusOffset;

        if (blocks.size() == 1 && isPlainParagraphRow(blocks.get(0))) {
            CharSequence ins = RichTextCell.readStyledText(blocks.get(0).block);
            if (ins == null) ins = "";
            SpannableStringBuilder sb = new SpannableStringBuilder(head);
            sb.append(ins);
            focusOffset = sb.length();
            sb.append(tail);
            RichTextCell.applyStyledTextToBlock(startRow.block, sb);
            for (int i = b; i > a; i--) rows.remove(i);
            focusRow = startRow;
        } else {
            ArrayList<BlockRow> work = new ArrayList<>(blocks);
            if (head.length() > 0) {
                TL_iv.PageBlock pre = newSameTypeBlock(startRow.block);
                RichTextCell.applyStyledTextToBlock(pre, head);
                work.add(0, new BlockRow(pre, startRow.level, startRow.num));
            }
            focusRow = blocks.get(blocks.size() - 1);
            focusOffset = caretEndOf(focusRow);
            if (tail.length() > 0) {
                TL_iv.PageBlock suf = newSameTypeBlock(endRow.block);
                RichTextCell.applyStyledTextToBlock(suf, tail);
                work.add(new BlockRow(suf, endRow.level, endRow.num));
            }
            for (int i = b; i >= a; i--) rows.remove(i);
            for (int i = 0; i < work.size(); i++) rows.add(a + i, work.get(i));
        }

        renumberAllRuns();
        final BlockRow fRow = focusRow;
        final int fo = focusOffset;
        textSelectionHelper.clear();
        adapter.update(false);
        if (history != null) history.record();
        post(() -> {
            View v = fRow == null ? null : findViewByItemObject(fRow);
            if (v instanceof RichTextCell) {
                RichTextCell cell = (RichTextCell) v;
                cell.requestEditFocus();
                int len = cell.getEditText().length();
                cell.getEditText().setSelection(Math.min(fo, len));
            }
        });
        return true;
    }

    private static boolean isPlainParagraphRow(BlockRow r) {
        return r != null && !r.detailsEnd && !isDetailsHeader(r)
            && r.block instanceof TL_iv.pageBlockParagraph
            && r.level == 0 && r.num == 0 && !r.checkbox
            && r.quoteIds.isEmpty();
    }

    private static TL_iv.PageBlock newSameTypeBlock(TL_iv.PageBlock proto) {
        if (proto instanceof TL_iv.pageBlockHeading1) return new TL_iv.pageBlockHeading1();
        if (proto instanceof TL_iv.pageBlockHeading2) return new TL_iv.pageBlockHeading2();
        if (proto instanceof TL_iv.pageBlockHeading3) return new TL_iv.pageBlockHeading3();
        if (proto instanceof TL_iv.pageBlockHeading4) return new TL_iv.pageBlockHeading4();
        if (proto instanceof TL_iv.pageBlockHeading5) return new TL_iv.pageBlockHeading5();
        if (proto instanceof TL_iv.pageBlockHeading6) return new TL_iv.pageBlockHeading6();
        if (proto instanceof TL_iv.pageBlockBlockquote) return new TL_iv.pageBlockBlockquote();
        if (proto instanceof TL_iv.pageBlockPullquote) return new TL_iv.pageBlockPullquote();
        if (proto instanceof TL_iv.pageBlockPreformatted) {
            TL_iv.pageBlockPreformatted p = new TL_iv.pageBlockPreformatted();
            p.language = ((TL_iv.pageBlockPreformatted) proto).language;
            return p;
        }
        if (proto instanceof TL_iv.pageBlockFooter) return new TL_iv.pageBlockFooter();
        return new TL_iv.pageBlockParagraph();
    }

    private static int caretEndOf(BlockRow r) {
        if (r == null) return 0;
        if (isDetailsHeader(r)) {
            CharSequence t = RichTextStyle.toSpannable(((TL_iv.pageBlockDetails) r.block).title);
            return t == null ? 0 : t.length();
        }
        if (isTextBearing(r.block)) {
            CharSequence t = RichTextCell.readStyledText(r.block);
            return t == null ? 0 : t.length();
        }
        return 0;
    }

    private static boolean isTextBearing(TL_iv.PageBlock b) {
        return b instanceof TL_iv.pageBlockParagraph
            || b instanceof TL_iv.pageBlockPreformatted
            || b instanceof TL_iv.pageBlockBlockquote
            || b instanceof TL_iv.pageBlockPullquote
            || isHeading(b);
    }

    private void deleteHelperSelection() {
        int sCell = textSelectionHelper.getStartCell();
        int sOff = textSelectionHelper.getStartOffset();
        int eCell = textSelectionHelper.getEndCell();
        int eOff = textSelectionHelper.getEndOffset();
        if (!applyEditRange(sCell, sOff, eCell, eOff, new String[] { "" })) {
            removeSelectedBlocks(sCell, eCell);
        }
    }

    private void replaceHelperSelectionWith(String s) {
        int sCell = textSelectionHelper.getStartCell();
        int sOff = textSelectionHelper.getStartOffset();
        int eCell = textSelectionHelper.getEndCell();
        int eOff = textSelectionHelper.getEndOffset();
        String[] lines = s.split("\n", -1);
        applyEditRange(sCell, sOff, eCell, eOff, lines);
    }

    private boolean applyEditRange(int sCell, int sOff, int eCell, int eOff, String[] insertedLines) {
        sCell = rows.indexOf(rowForCell(sCell));
        eCell = rows.indexOf(rowForCell(eCell));
        if (sCell < 0 || eCell < 0 || sCell >= rows.size() || eCell >= rows.size()) return false;

        BlockRow startRow = rows.get(sCell);
        BlockRow endRow = rows.get(eCell);

        if (sCell == eCell && isDetailsHeader(startRow)) {
            applyEditInsideDetails(startRow, sOff, eOff, insertedLines);
            return true;
        }
        boolean crossesDetails = false;
        for (int i = sCell; i <= eCell; i++) {
            if (rows.get(i).detailsEnd || isDetailsHeader(rows.get(i))) { crossesDetails = true; break; }
        }
        if (crossesDetails) {
            deleteAcrossDetails(sCell, sOff, eCell, eOff, insertedLines);
            return true;
        }
        if (history != null) history.flush();

        boolean startInTable = startRow.block instanceof TL_iv.pageBlockTable;
        boolean endInTable = endRow.block instanceof TL_iv.pageBlockTable;
        if (startInTable || endInTable) {
            if (startInTable && endInTable && sCell == eCell) {
                int sChild = textSelectionHelper.getStartChildPosition();
                int eChild = textSelectionHelper.getEndChildPosition();
                applyEditInsideTable(startRow, sChild, sOff, eChild, eOff, insertedLines);
                return true;
            }
            return false;
        }

        boolean startCaption = hasCaption(startRow.block);
        boolean endCaption = hasCaption(endRow.block);
        if (startCaption || endCaption) {
            if (startCaption && endCaption && sCell == eCell) {
                applyEditInsideCaption(startRow, sOff, eOff, insertedLines);
                return true;
            }
            return false;
        }

        if (sCell == eCell && RichTextCell.isQuoteBlock(startRow.block)
            && textSelectionHelper.getStartChildPosition() == 1
            && textSelectionHelper.getEndChildPosition() == 1) {
            applyEditInsideAuthor(startRow, sOff, eOff, insertedLines);
            return true;
        }

        String startText = RichTextCell.readPlainText(startRow.block);
        String endText = sCell == eCell ? startText : RichTextCell.readPlainText(endRow.block);
        sOff = Math.max(0, Math.min(sOff, startText.length()));
        eOff = Math.max(0, Math.min(eOff, endText.length()));

        String head = startText.substring(0, sOff);
        String tail = endText.substring(eOff);

        int finalCell;
        int finalOffset;

        if (insertedLines.length <= 1) {
            String inserted = insertedLines.length == 0 ? "" : insertedLines[0];
            RichTextCell.applyTextToBlock(startRow.block, head + inserted + tail);
            if (eCell > sCell) {
                for (int i = eCell; i > sCell; i--) rows.remove(i);
            }
            finalCell = sCell;
            finalOffset = head.length() + inserted.length();
        } else {
            RichTextCell.applyTextToBlock(startRow.block, head + insertedLines[0]);
            if (eCell > sCell) {
                for (int i = eCell; i > sCell; i--) rows.remove(i);
            }
            for (int i = 1; i < insertedLines.length - 1; i++) {
                TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
                applyPlainText(p, insertedLines[i]);
                rows.add(sCell + i, new BlockRow(p, startRow.level, startRow.num > 0 ? startRow.num + i : startRow.num));
            }
            String lastLine = insertedLines[insertedLines.length - 1];
            TL_iv.pageBlockParagraph tailBlock = new TL_iv.pageBlockParagraph();
            applyPlainText(tailBlock, lastLine + tail);
            rows.add(sCell + insertedLines.length - 1, new BlockRow(tailBlock, startRow.level, startRow.num > 0 ? startRow.num + insertedLines.length - 1 : startRow.num));
            finalCell = sCell + insertedLines.length - 1;
            finalOffset = lastLine.length();
        }
        renumberAllRuns();

        final BlockRow focusRow = finalCell >= 0 && finalCell < rows.size() ? rows.get(finalCell) : null;
        textSelectionHelper.clear();
        adapter.update(false);
        if (history != null) history.record();

        final int focusOffset = finalOffset;
        post(() -> {
            View v = focusRow == null ? null : findViewByItemObject(focusRow);
            if (v instanceof RichTextCell) {
                RichTextCell cell = (RichTextCell) v;
                cell.requestEditFocus();
                int len = cell.getEditText().length();
                cell.getEditText().setSelection(Math.min(focusOffset, len));
            }
        });
        return true;
    }

    private void removeSelectedBlocks(int startPos, int endPos) {
        if (startPos > endPos) { final int t = startPos; startPos = endPos; endPos = t; }
        final ArrayList<BlockRow> toRemove = new ArrayList<>();
        for (int i = Math.max(0, startPos); i <= endPos && i < itemRows.size(); i++) {
            final BlockRow r = itemRows.get(i);
            if (r.authorQuoteId != 0 || rows.indexOf(r) < 0) continue;
            toRemove.add(r);
        }
        if (toRemove.isEmpty()) return;
        if (history != null) history.flush();
        final int firstIdx = rows.indexOf(toRemove.get(0));
        rows.removeAll(toRemove);
        if (rows.isEmpty()) rows.add(new BlockRow(new TL_iv.pageBlockParagraph()));
        gcQuoteAuthors();
        collapseSingleBlockQuotes();
        normalizeNestedQuotes();
        renumberAllRuns();
        if (textSelectionHelper != null) textSelectionHelper.clear();
        adapter.update(false);
        if (history != null) history.record();
        final BlockRow focus = rows.get(Math.max(0, Math.min(firstIdx, rows.size() - 1)));
        post(() -> focusRow(focus));
    }

    private void gcQuoteAuthors() {
        if (quoteAuthors.isEmpty()) return;
        final HashSet<Long> live = new HashSet<>();
        for (int i = 0; i < rows.size(); i++) live.addAll(rows.get(i).quoteIds);
        quoteAuthors.keySet().retainAll(live);
    }

    private void applyEditInsideTableTitle(RichTableCell rtc, BlockRow row, int sOff, int eOff, String[] insertedLines) {
        if (!(row.block instanceof TL_iv.pageBlockTable)) return;
        final TL_iv.pageBlockTable tb = (TL_iv.pageBlockTable) row.block;
        if (history != null) history.flush();

        final String text = RichTextStyle.plainOf(tb.title);
        final int from = Math.max(0, Math.min(Math.min(sOff, eOff), text.length()));
        final int to = Math.max(0, Math.min(Math.max(sOff, eOff), text.length()));

        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < insertedLines.length; i++) {
            if (i > 0) joined.append(' ');
            joined.append(insertedLines[i]);
        }
        final String insert = joined.toString();
        final String newText = text.substring(0, from) + insert + text.substring(to);

        final TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = newText;
        tb.title = plain;

        final RichEditText et = rtc.getTitleEditText();
        et.setTextSilently(newText);
        et.invalidateEffects();

        final int caret = from + insert.length();
        textSelectionHelper.clear();
        if (history != null) history.record();
        delegate.onContentChanged();

        post(() -> {
            et.requestEditFocus();
            final int l = et.length();
            et.setSelection(Math.max(0, Math.min(caret, l)));
        });
    }

    private void applyEditInsideAuthor(BlockRow row, int sOff, int eOff, String[] insertedLines) {
        final View v = findViewByItemObject(row);
        if (!(v instanceof RichTextCell)) return;
        final RichTextCell cell = (RichTextCell) v;
        final RichEditText et = cell.getAuthorEditText();
        if (history != null) history.flush();

        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < insertedLines.length; i++) {
            if (i > 0) joined.append(' ');
            joined.append(insertedLines[i]);
        }
        final String insert = joined.toString();

        final SpannableStringBuilder sb = new SpannableStringBuilder(et.getText());
        final int len = sb.length();
        final int from = Math.max(0, Math.min(Math.min(sOff, eOff), len));
        final int to = Math.max(0, Math.min(Math.max(sOff, eOff), len));
        sb.replace(from, to, insert);
        et.setTextSilently(sb);
        et.invalidateEffects();
        cell.persistAuthor();

        final int caret = from + insert.length();
        textSelectionHelper.clear();
        if (history != null) history.record();
        delegate.onContentChanged();

        post(() -> {
            et.requestEditFocus();
            int l = et.length();
            et.setSelection(Math.max(0, Math.min(caret, l)));
        });
    }

    RichEditText getFocusedEditTextOrNull() {
        View focused = findFocus();
        return focused instanceof RichEditText ? (RichEditText) focused : null;
    }

    RichEditText findFocusedEditText() {
        View focused = findFocus();
        if (focused instanceof RichEditText) return (RichEditText) focused;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v instanceof RichTextCell) {
                return ((RichTextCell) v).getEditText();
            }
        }
        return null;
    }

    private static void applyPlainText(TL_iv.PageBlock block, String text) {
        TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = text;
        if (block instanceof TL_iv.pageBlockParagraph) {
            ((TL_iv.pageBlockParagraph) block).text = plain;
        }
    }

    private int detailsDepthBefore(int index) {
        int d = 0;
        for (int i = 0; i < index && i < rows.size(); i++) {
            if (isDetailsHeader(rows.get(i))) d++;
            else if (rows.get(i).detailsEnd) d--;
        }
        return d;
    }

    private void deleteAcrossDetails(int sCell, int sOff, int eCell, int eOff, String[] insertedLines) {
        int lo = sCell, hi = eCell;
        while (lo > 0 && detailsDepthBefore(lo) > 0) lo--;
        while (hi + 1 < rows.size() && detailsDepthBefore(hi + 1) > 0) hi++;

        final BlockRow startRow = rows.get(sCell);
        final BlockRow endRow = rows.get(eCell);
        final boolean deleteStartFully = lo != sCell || isDetailsHeader(startRow) || startRow.detailsEnd;
        final boolean deleteEndFully = hi != eCell || isDetailsHeader(endRow) || endRow.detailsEnd;

        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < insertedLines.length; i++) {
            if (i > 0) joined.append(' ');
            joined.append(insertedLines[i]);
        }
        final String inserted = joined.toString();

        if (history != null) history.flush();

        BlockRow focusRow;
        int focusOff;
        if (!deleteStartFully) {
            final String startText = RichTextCell.readPlainText(startRow.block);
            final int sOffC = Math.max(0, Math.min(sOff, startText.length()));
            final String head = startText.substring(0, sOffC);
            String tail = "";
            if (!deleteEndFully) {
                final String endText = RichTextCell.readPlainText(endRow.block);
                tail = endText.substring(Math.max(0, Math.min(eOff, endText.length())));
            }
            RichTextCell.applyTextToBlock(startRow.block, head + inserted + tail);
            rows.subList(lo + 1, hi + 1).clear();
            focusRow = startRow;
            focusOff = head.length() + inserted.length();
        } else if (!deleteEndFully) {
            final String endText = RichTextCell.readPlainText(endRow.block);
            final String tail = endText.substring(Math.max(0, Math.min(eOff, endText.length())));
            RichTextCell.applyTextToBlock(endRow.block, inserted + tail);
            rows.subList(lo, hi).clear();
            focusRow = endRow;
            focusOff = inserted.length();
        } else {
            rows.subList(lo, hi + 1).clear();
            final TL_iv.pageBlockParagraph p = new TL_iv.pageBlockParagraph();
            applyPlainText(p, inserted);
            focusRow = new BlockRow(p);
            rows.add(Math.min(lo, rows.size()), focusRow);
            focusOff = inserted.length();
        }
        if (rows.isEmpty()) {
            focusRow = new BlockRow(new TL_iv.pageBlockParagraph());
            rows.add(focusRow);
            focusOff = 0;
        }

        renumberAllRuns();
        textSelectionHelper.clear();
        adapter.update(false);
        if (history != null) history.record();

        final BlockRow fr = focusRow;
        final int fo = focusOff;
        post(() -> {
            final View v = findViewByItemObject(fr);
            if (v instanceof RichTextCell) {
                final RichTextCell c = (RichTextCell) v;
                c.requestEditFocus();
                final int len = c.getEditText().length();
                c.getEditText().setSelection(Math.max(0, Math.min(fo, len)));
            }
        });
    }

    private void applyEditInsideDetails(BlockRow row, int sOff, int eOff, String[] insertedLines) {
        final View v = findViewByItemObject(row);
        if (!(v instanceof RichDetailsCell) || !(row.block instanceof TL_iv.pageBlockDetails)) return;
        final RichEditText et = ((RichDetailsCell) v).getEditText();
        if (history != null) history.flush();

        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < insertedLines.length; i++) {
            if (i > 0) joined.append(' ');
            joined.append(insertedLines[i]);
        }
        final String insert = joined.toString();

        final SpannableStringBuilder sb = new SpannableStringBuilder(et.getText());
        final int len = sb.length();
        final int from = Math.max(0, Math.min(Math.min(sOff, eOff), len));
        final int to = Math.max(0, Math.min(Math.max(sOff, eOff), len));
        sb.replace(from, to, insert);
        et.setTextSilently(sb);
        et.invalidateEffects();
        ((TL_iv.pageBlockDetails) row.block).title = RichTextStyle.fromSpannable(sb);

        final int caret = from + insert.length();
        textSelectionHelper.clear();
        if (history != null) history.record();

        post(() -> {
            et.requestEditFocus();
            int l = et.length();
            et.setSelection(Math.max(0, Math.min(caret, l)));
        });
    }

    private void applyEditInsideCaption(BlockRow row, int sOff, int eOff, String[] insertedLines) {
        final View v = findViewByItemObject(row);
        if (!(v instanceof RichCaptionHost)) return;
        final RichEditText et = ((RichCaptionHost) v).getCaptionEditText();
        if (history != null) history.flush();

        final StringBuilder joined = new StringBuilder();
        for (int i = 0; i < insertedLines.length; i++) {
            if (i > 0) joined.append(' ');
            joined.append(insertedLines[i]);
        }
        final String insert = joined.toString();

        final SpannableStringBuilder sb = new SpannableStringBuilder(et.getText());
        final int len = sb.length();
        final int from = Math.max(0, Math.min(Math.min(sOff, eOff), len));
        final int to = Math.max(0, Math.min(Math.max(sOff, eOff), len));
        sb.replace(from, to, insert);
        et.setTextSilently(sb);
        et.invalidateEffects();
        ((RichCaptionHost) v).persistCaption();

        final int caret = from + insert.length();
        textSelectionHelper.clear();
        if (history != null) history.record();

        post(() -> {
            et.requestEditFocus();
            int l = et.length();
            et.setSelection(Math.max(0, Math.min(caret, l)));
        });
    }

    private void applyEditInsideTable(BlockRow row, int sChild, int sOff, int eChild, int eOff, String[] insertedLines) {
        View v = findViewByItemObject(row);
        if (!(v instanceof RichTableCell)) return;
        RichTableCell rtc = (RichTableCell) v;
        TableModel model = rtc.getModel();
        if (model == null) return;

        if (sChild == 0 && eChild == 0) {
            applyEditInsideTableTitle(rtc, row, sOff, eOff, insertedLines);
            return;
        }
        sChild -= 1;
        eChild -= 1;
        int childCount = model.anchors().size();
        if (sChild < 0 || sChild >= childCount || eChild < 0 || eChild >= childCount) return;
        if (history != null) history.flush();

        if (sChild > eChild || (sChild == eChild && sOff > eOff)) {
            int tc = sChild; sChild = eChild; eChild = tc;
            int to = sOff; sOff = eOff; eOff = to;
        }

        StringBuilder joined = new StringBuilder();
        for (int i = 0; i < insertedLines.length; i++) {
            if (i > 0) joined.append('\n');
            joined.append(insertedLines[i]);
        }
        String insert = joined.toString();

        TL_iv.pageTableCell startCell = model.anchors().get(sChild);
        TL_iv.pageTableCell endCell = model.anchors().get(eChild);

        TL_iv.pageTableCell focusCell;
        int focusOffset;

        if (sChild == eChild) {
            String text = TableModel.readPlainText(startCell);
            sOff = Math.max(0, Math.min(sOff, text.length()));
            eOff = Math.max(0, Math.min(eOff, text.length()));
            String newText = text.substring(0, sOff) + insert + text.substring(eOff);
            TableModel.applyPlainText(startCell, newText);
            RichTableCellHost host = rtc.getGrid().hostForAnchor(startCell);
            if (host != null) host.editText.setTextSilently(newText);
            focusCell = startCell;
            focusOffset = sOff + insert.length();
        } else {
            String startText = TableModel.readPlainText(startCell);
            sOff = Math.max(0, Math.min(sOff, startText.length()));
            String newStart = startText.substring(0, sOff) + insert;
            TableModel.applyPlainText(startCell, newStart);
            RichTableCellHost startHost = rtc.getGrid().hostForAnchor(startCell);
            if (startHost != null) startHost.editText.setTextSilently(newStart);

            for (int i = sChild + 1; i < eChild; i++) {
                TL_iv.pageTableCell c = model.anchors().get(i);
                TableModel.applyPlainText(c, "");
                RichTableCellHost h = rtc.getGrid().hostForAnchor(c);
                if (h != null) h.editText.setTextSilently("");
            }

            String endText = TableModel.readPlainText(endCell);
            eOff = Math.max(0, Math.min(eOff, endText.length()));
            String newEnd = endText.substring(eOff);
            TableModel.applyPlainText(endCell, newEnd);
            RichTableCellHost endHost = rtc.getGrid().hostForAnchor(endCell);
            if (endHost != null) endHost.editText.setTextSilently(newEnd);

            focusCell = startCell;
            focusOffset = sOff + insert.length();
        }

        textSelectionHelper.clear();
        if (history != null) history.record();

        final TL_iv.pageTableCell finalFocusCell = focusCell;
        final int finalFocusOffset = focusOffset;
        post(() -> {
            RichTableCellHost host = rtc.getGrid().hostForAnchor(finalFocusCell);
            if (host == null) return;
            host.editText.requestEditFocus();
            int len = host.editText.length();
            host.editText.setSelection(Math.max(0, Math.min(finalFocusOffset, len)));
        });
    }
}
