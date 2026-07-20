package org.telegram.ui.iv;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.tgnet.SerializedData;
import org.telegram.tgnet.TLRPC;
import org.telegram.tgnet.tl.TL_iv;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class RichEditorHistory {

    private static final long DEBOUNCE_MS = 800;
    private static final int MAX_DEPTH = 150;
    public static final int LARGE_CHANGE_THRESHOLD = 16;

    public interface Delegate {
        ArrayList<BlockRow> getRows();
        void restoreRows(List<BlockRow> rows, FocusState focus);
        void onHistoryChanged();
        FocusState captureFocus();
    }

    public static final class FocusState {
        public final long rowId;     // -1 if nothing focused
        public final int childIndex; // table anchor index, -1 if not a table cell
        public final int selStart;
        public final int selEnd;

        public FocusState(long rowId, int childIndex, int selStart, int selEnd) {
            this.rowId = rowId;
            this.childIndex = childIndex;
            this.selStart = selStart;
            this.selEnd = selEnd;
        }

        public static final FocusState NONE = new FocusState(-1, -1, 0, 0);
    }

    private static final class RowState {
        final long id;
        final byte[] blockData;
        final int level;
        final int num;
        final boolean checkbox;
        final boolean checked;
        final boolean detailsEnd;
        final MediaUploadState media;
        final ArrayList<MediaUploadState> medias;
        final ArrayList<Long> quoteIds;

        RowState(long id, byte[] blockData, int level, int num, boolean checkbox, boolean checked, boolean detailsEnd, MediaUploadState media, ArrayList<MediaUploadState> medias, ArrayList<Long> quoteIds) {
            this.id = id;
            this.blockData = blockData;
            this.level = level;
            this.num = num;
            this.checkbox = checkbox;
            this.checked = checked;
            this.detailsEnd = detailsEnd;
            this.media = media;
            this.medias = medias;
            this.quoteIds = quoteIds;
        }
    }

    private static final class Snapshot {
        final RowState[] rows;
        final FocusState focus;

        Snapshot(RowState[] rows, FocusState focus) {
            this.rows = rows;
            this.focus = focus;
        }
    }

    private final Delegate delegate;
    private final ArrayDeque<Snapshot> undoStack = new ArrayDeque<>();
    private final ArrayDeque<Snapshot> redoStack = new ArrayDeque<>();
    private Snapshot baseline;
    private boolean dirty;
    private boolean restoring;

    private final Runnable commitRunnable = this::commit;

    public RichEditorHistory(Delegate delegate) {
        this.delegate = delegate;
        baseline = capture();
    }

    public void onTyping() {
        if (restoring) return;
        dirty = true;
        AndroidUtilities.cancelRunOnUIThread(commitRunnable);
        AndroidUtilities.runOnUIThread(commitRunnable, DEBOUNCE_MS);
        delegate.onHistoryChanged();
    }

    public void onBeforeChange(int removed, int added) {
        if (restoring) return;
        if (removed > LARGE_CHANGE_THRESHOLD || added > LARGE_CHANGE_THRESHOLD) {
            flush();
        }
    }

    public void flush() {
        AndroidUtilities.cancelRunOnUIThread(commitRunnable);
        commit();
    }

    public void record() {
        if (restoring) return;
        AndroidUtilities.cancelRunOnUIThread(commitRunnable);
        dirty = true;
        commit();
    }

    /** Re-establishes the baseline as the current content and clears undo/redo. Call after loading
     *  initial content so the first undo returns to that content instead of the empty state captured
     *  at construction. */
    public void resetBaseline() {
        AndroidUtilities.cancelRunOnUIThread(commitRunnable);
        undoStack.clear();
        redoStack.clear();
        baseline = capture();
        dirty = false;
        delegate.onHistoryChanged();
    }

    public boolean canUndo() {
        return dirty || !undoStack.isEmpty();
    }

    public boolean canRedo() {
        return !redoStack.isEmpty();
    }

    public void undo() {
        flush();
        if (undoStack.isEmpty()) return;
        redoStack.addLast(baseline);
        baseline = undoStack.removeLast();
        applyRestore(baseline);
    }

    public void redo() {
        flush();
        if (redoStack.isEmpty()) return;
        undoStack.addLast(baseline);
        baseline = redoStack.removeLast();
        applyRestore(baseline);
    }

    private void commit() {
        AndroidUtilities.cancelRunOnUIThread(commitRunnable);
        if (!dirty || restoring) return;
        Snapshot now = capture();
        dirty = false;
        if (sameAs(baseline, now)) {
            return;
        }
        undoStack.addLast(baseline);
        while (undoStack.size() > MAX_DEPTH) {
            undoStack.removeFirst();
        }
        redoStack.clear();
        baseline = now;
        delegate.onHistoryChanged();
    }

    private void applyRestore(Snapshot snapshot) {
        dirty = false;
        restoring = true;
        ArrayList<BlockRow> rows = new ArrayList<>(snapshot.rows.length);
        for (RowState rs : snapshot.rows) {
            BlockRow row = new BlockRow(deserializeBlock(rs.blockData), rs.level, rs.num, rs.id);
            row.checkbox = rs.checkbox;
            row.checked = rs.checked;
            row.detailsEnd = rs.detailsEnd;
            row.media = rs.media;
            row.medias = rs.medias != null ? new ArrayList<>(rs.medias) : null;
            if (rs.quoteIds != null) row.quoteIds.addAll(rs.quoteIds);
            rows.add(row);
        }
        delegate.restoreRows(rows, snapshot.focus);
        restoring = false;
        delegate.onHistoryChanged();
    }

    private Snapshot capture() {
        ArrayList<BlockRow> rows = delegate.getRows();
        HashMap<Long, RowState> prev = new HashMap<>();
        if (baseline != null) {
            for (RowState rs : baseline.rows) prev.put(rs.id, rs);
        }
        RowState[] states = new RowState[rows.size()];
        for (int i = 0; i < rows.size(); i++) {
            BlockRow row = rows.get(i);
            byte[] data = serializeBlock(row.block);
            RowState old = prev.get(row.id);
            if (old != null
                    && old.level == row.level
                    && old.num == row.num
                    && old.checkbox == row.checkbox
                    && old.checked == row.checked
                    && old.detailsEnd == row.detailsEnd
                    && old.media == row.media
                    && sameMedias(old.medias, row.medias)
                    && old.quoteIds.equals(row.quoteIds)
                    && Arrays.equals(old.blockData, data)) {
                states[i] = old; // share — unchanged row
            } else {
                states[i] = new RowState(row.id, data, row.level, row.num, row.checkbox, row.checked, row.detailsEnd, row.media,
                    row.medias != null ? new ArrayList<>(row.medias) : null, new ArrayList<>(row.quoteIds));
            }
        }
        return new Snapshot(states, delegate.captureFocus());
    }

    private static boolean sameAs(Snapshot a, Snapshot b) {
        if (a == null || b == null) return false;
        if (a.rows.length != b.rows.length) return false;
        for (int i = 0; i < a.rows.length; i++) {
            if (a.rows[i] != b.rows[i]) return false; // capture() shares unchanged RowState by ref
        }
        return true;
    }

    private static boolean sameMedias(ArrayList<MediaUploadState> a, ArrayList<MediaUploadState> b) {
        if (a == b) return true;
        if (a == null || b == null || a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (a.get(i) != b.get(i)) return false;
        }
        return true;
    }

    private static byte[] serializeBlock(TL_iv.PageBlock block) {
        normalize(block);
        SerializedData data = new SerializedData(block.getObjectSize());
        block.serializeToStream(data);
        byte[] bytes = data.toByteArray();
        data.cleanup();
        return bytes;
    }

    private static TL_iv.PageBlock deserializeBlock(byte[] bytes) {
        try {
            SerializedData data = new SerializedData(bytes);
            TL_iv.PageBlock block = TL_iv.PageBlock.TLdeserialize(data, data.readInt32(true), true);
            data.cleanup();
            if (block != null) return block;
        } catch (Throwable t) {
            FileLog.e(t);
        }
        TL_iv.pageBlockParagraph fallback = new TL_iv.pageBlockParagraph();
        fallback.text = new TL_iv.textEmpty();
        return fallback;
    }

    private static void normalize(TL_iv.PageBlock b) {
        if (b == null) return;
        if (b.text == null) b.text = emptyRichText();
        if (b.caption == null) b.caption = emptyCaption();
        if (b instanceof TL_iv.pageBlockBlockquote) {
            TL_iv.pageBlockBlockquote bq = (TL_iv.pageBlockBlockquote) b;
            if (bq.caption == null) bq.caption = emptyRichText();
        } else if (b instanceof TL_iv.pageBlockBlockquoteBlocks) {
            TL_iv.pageBlockBlockquoteBlocks bq = (TL_iv.pageBlockBlockquoteBlocks) b;
            if (bq.blocks == null) bq.blocks = new ArrayList<>();
            for (TL_iv.PageBlock nested : bq.blocks) normalize(nested);
            if (bq.caption == null) bq.caption = emptyRichText();
        } else if (b instanceof TL_iv.pageBlockPullquote) {
            TL_iv.pageBlockPullquote pq = (TL_iv.pageBlockPullquote) b;
            if (pq.caption == null) pq.caption = emptyRichText();
        }
        if (b instanceof TL_iv.pageBlockPreformatted) {
            TL_iv.pageBlockPreformatted p = (TL_iv.pageBlockPreformatted) b;
            if (p.language == null) p.language = "";
        } else if (b instanceof TL_iv.pageBlockMath) {
            TL_iv.pageBlockMath m = (TL_iv.pageBlockMath) b;
            if (m.source == null) m.source = "";
        } else if (b instanceof TL_iv.pageBlockMap) {
            TL_iv.pageBlockMap m = (TL_iv.pageBlockMap) b;
            if (m.geo == null) m.geo = new TLRPC.TL_geoPointEmpty();
        } else if (b instanceof TL_iv.pageBlockTable) {
            TL_iv.pageBlockTable t = (TL_iv.pageBlockTable) b;
            if (t.title == null) t.title = emptyRichText();
            if (t.rows == null) t.rows = new ArrayList<>();
            for (TL_iv.pageTableRow row : t.rows) {
                if (row == null) continue;
                if (row.cells == null) row.cells = new ArrayList<>();
                for (TL_iv.pageTableCell cell : row.cells) {
                    if (cell != null && cell.text == null) cell.text = emptyRichText();
                }
            }
        } else if (b instanceof TL_iv.pageBlockCollage) {
            TL_iv.pageBlockCollage c = (TL_iv.pageBlockCollage) b;
            if (c.items == null) c.items = new ArrayList<>();
            for (TL_iv.PageBlock nested : c.items) normalize(nested);
        } else if (b instanceof TL_iv.pageBlockSlideshow) {
            TL_iv.pageBlockSlideshow c = (TL_iv.pageBlockSlideshow) b;
            if (c.items == null) c.items = new ArrayList<>();
            for (TL_iv.PageBlock nested : c.items) normalize(nested);
        }
    }

    private static TL_iv.RichText emptyRichText() {
        return new TL_iv.textEmpty();
    }

    private static TL_iv.PageCaption emptyCaption() {
        TL_iv.PageCaption c = new TL_iv.PageCaption();
        c.text = emptyRichText();
        c.credit = emptyRichText();
        return c;
    }
}
