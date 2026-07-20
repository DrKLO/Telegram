package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.InputType;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;

import org.telegram.messenger.Emoji;
import org.telegram.messenger.SharedConfig;
import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.TextSelectionHelper;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Set;

public class RichTableCell extends RichBlockCell implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onTextChanged(BlockRow row);
        default void onTextWillChange(BlockRow row, int removed, int added) {}
        default void onSpansChanged(BlockRow row) {}
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
        default void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) {}
        default void onLockedInsert(CharSequence text) {}
        default boolean onSelectAll(BlockRow row) { return false; }
    }

    private final Theme.ResourcesProvider resourcesProvider;
    private final RichEditText titleEditText;
    private final HorizontalScrollView scrollView;
    private final RichTableCellGrid grid;
    private final ScrollContent scrollContent;
    private final ArrayList<TextSelectionHelper.TextLayoutBlock> tmpBlocks = new ArrayList<>();

    private boolean blockRtl;
    private Delegate delegate;
    private TableModel model;
    private boolean hijackingSelection;
    private final java.util.LinkedHashSet<TL_iv.pageTableCell> selectedCells = new java.util.LinkedHashSet<>();
    private CellSelectionListener cellSelectionListener;

    public interface CellSelectionListener {
        void onCellSelectionChanged(RichTableCell table);
    }

    public RichTableCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        titleEditText = new RichEditText(context, resourcesProvider);
        titleEditText.setAllowNewlines(false);
        titleEditText.setInputType(
            InputType.TYPE_CLASS_TEXT |
            InputType.TYPE_TEXT_FLAG_MULTI_LINE |
            InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        );
        titleEditText.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.TOP);
        titleEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, Math.max(8, SharedConfig.fontSize));
        titleEditText.setPadding(dp(2), dp(4), dp(2), dp(2));
        titleEditText.setHint("Add title…");
        titleEditText.setCenterEmptyHint(true);
        titleEditText.setListener(new RichEditText.Listener() {
            @Override
            public void onTextWillChange(RichEditText et, int removed, int added) {
                if (delegate != null && currentRow != null) delegate.onTextWillChange(currentRow, removed, added);
            }

            @Override
            public void onTextChanged(RichEditText et, Editable text) {
                persistTitle();
                if (delegate != null && currentRow != null) delegate.onTextChanged(currentRow);
            }

            @Override
            public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {
                if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard);
            }

            @Override
            public void onLockedInsert(RichEditText et, CharSequence text) {
                if (delegate != null) delegate.onLockedInsert(text);
            }

            @Override
            public boolean onSelectAll(RichEditText et) {
                if (delegate != null && currentRow != null) return delegate.onSelectAll(currentRow);
                return false;
            }

            @Override
            public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                if (hijackingSelection || selStart == selEnd || delegate == null) return;
                final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                if (helper == null) return;
                if (helper.isInSelectionMode() && helper.getSelectedCell() == RichTableCell.this) return;
                final int s = selStart, e = selEnd;
                final int childPos = titleChildPos();
                if (childPos < 0) return;
                post(() -> {
                    if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                    if (helper.selectRangeOf(RichTableCell.this, childPos, s, e)) {
                        hijackingSelection = true;
                        et.setSelection(e);
                        hijackingSelection = false;
                    }
                });
            }
        });
        titleEditText.setDelegate(() -> {
            persistTitle();
            if (delegate != null && currentRow != null) delegate.onSpansChanged(currentRow);
        });
        addView(titleEditText);

        scrollView = new HorizontalScrollView(context) {
            @Override
            protected void onScrollChanged(int l, int t, int oldl, int oldt) {
                super.onScrollChanged(l, t, oldl, oldt);
                if (delegate != null) {
                    TextSelectionHelper.ArticleTextSelectionHelper h = delegate.getSelectionHelper();
                    if (h != null && h.isInSelectionMode()) {
                        h.invalidate();
                    }
                }
                invalidate();
            }
        };
        scrollView.setClipToPadding(false);
        scrollView.setPadding(dp(16) - dp(RichTableCellGrid.HANDLE_PAD_DP - RichTableCellGrid.GRID_PADDING_DP), 0, dp(16), 0);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 6, 0, 0));

        grid = new RichTableCellGrid(context, resourcesProvider);

        scrollContent = new ScrollContent(context);
        scrollContent.addView(grid);
        scrollView.addView(scrollContent, new HorizontalScrollView.LayoutParams(HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));

        setWillNotDraw(false);
    }

    private final class ScrollContent extends ViewGroup {
        ScrollContent(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int parentW = MeasureSpec.getSize(widthMeasureSpec);
            int availableForGrid = Math.max(0, parentW);
            grid.measure(MeasureSpec.makeMeasureSpec(availableForGrid, MeasureSpec.AT_MOST), heightMeasureSpec);
            int gridW = grid.getMeasuredWidth();
            int gridH = grid.getMeasuredHeight();
            setMeasuredDimension(gridW, gridH);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int gridW = grid.getMeasuredWidth();
            int gridH = grid.getMeasuredHeight();
            grid.layout(0, 0, gridW, gridH);
        }
    }

    @Override
    protected void onBlockInsetChanged(int px) { requestLayout(); }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        blockRtl = RichBlockChrome.rtl();
        bindBlockInset(row);
        if (!(row.block instanceof TL_iv.pageBlockTable)) return;
        this.model = new TableModel((TL_iv.pageBlockTable) row.block);
        grid.setModel(model);
        grid.setSelectionProvider(selectedCells::contains);
        wireCellListeners();
        bindTitle();
        updateColors();
        scrollContent.requestLayout();
    }

    private void bindTitle() {
        if (currentRow == null || !(currentRow.block instanceof TL_iv.pageBlockTable)) return;
        final TL_iv.pageBlockTable tb = (TL_iv.pageBlockTable) currentRow.block;
        if (tb.title == null) tb.title = new TL_iv.textEmpty();
        final String plain = RichTextStyle.plainOf(tb.title);
        if (!String.valueOf(titleEditText.getText()).equals(plain)) {
            final CharSequence styled = Emoji.replaceEmoji(RichTextStyle.toSpannable(tb.title), titleEditText.getPaint().getFontMetricsInt(), false);
            titleEditText.setTextSilently(styled);
            titleEditText.invalidateEffects();
        }
    }

    private void persistTitle() {
        if (currentRow == null || !(currentRow.block instanceof TL_iv.pageBlockTable)) return;
        ((TL_iv.pageBlockTable) currentRow.block).title = RichTextStyle.fromSpannable(titleEditText.getText());
    }

    // Child-position scheme for the selection helper: the title is drawn on top of the table, so it
    // is child 0; the cell anchors follow as 1..N (cell i == anchors().get(childPos - 1)).
    public int titleChildPos() {
        return 0;
    }

    public int childCount() {
        return (model != null ? model.anchors().size() : 0) + 1;
    }

    public TL_iv.pageTableCell anchorForChildPos(int childPos) {
        if (model == null || childPos <= 0) return null;
        final int idx = childPos - 1;
        return idx < model.anchors().size() ? model.anchors().get(idx) : null;
    }

    public int childPosForAnchor(TL_iv.pageTableCell cell) {
        if (model == null) return -1;
        final int idx = model.flatIndexOfAnchor(cell);
        return idx < 0 ? -1 : idx + 1;
    }

    public RichEditText editTextForChildPos(int childPos) {
        if (childPos == 0) return titleEditText;
        final TL_iv.pageTableCell cell = anchorForChildPos(childPos);
        if (cell == null) return null;
        final RichTableCellHost host = grid.hostForAnchor(cell);
        return host != null ? host.editText : null;
    }

    public RichEditText getTitleEditText() {
        return titleEditText;
    }

    // Live text length of a selection child (title == 0, cells == 1..N).
    public int childTextLength(int childPos) {
        final RichEditText et = editTextForChildPos(childPos);
        return et != null ? et.length() : 0;
    }

    public void persistTitleFromEditor() {
        persistTitle();
    }

    public boolean isPressOnTitle(int localX, int localY) {
        final Layout layout = titleEditText.getLayout();
        if (layout == null) return false;
        final int textX = localX - (titleEditText.getLeft() + titleEditText.getPaddingLeft());
        final int textY = localY - (titleEditText.getTop() + titleEditText.getPaddingTop());
        if (textY < 0 || textY >= layout.getHeight()) return false;
        final int line = layout.getLineForVertical(textY);
        if (line < 0 || line >= layout.getLineCount()) return false;
        return textX >= layout.getLineLeft(line) && textX <= layout.getLineRight(line);
    }

    public void addRow() {
        if (model == null) return;
        model.addRow();
        refreshAfterModelChange();
    }

    public void addColumn() {
        if (model == null) return;
        model.addColumn();
        refreshAfterModelChange();
    }

    public Set<TL_iv.pageTableCell> getSelectedCells() {
        return selectedCells;
    }

    public boolean hasCellSelection() {
        return !selectedCells.isEmpty();
    }

    public void clearCellSelection() {
        if (selectedCells.isEmpty()) return;
        selectedCells.clear();
        grid.invalidate();
        notifyCellSelectionChanged();
    }

    public void toggleCellSelection(TL_iv.pageTableCell cell) {
        if (cell == null) return;
        if (!selectedCells.remove(cell)) selectedCells.add(cell);
        grid.invalidate();
        notifyCellSelectionChanged();
    }

    public void addCellToSelection(TL_iv.pageTableCell cell) {
        if (cell == null) return;
        if (selectedCells.add(cell)) {
            grid.invalidate();
            notifyCellSelectionChanged();
        }
    }

    public void setCellSelectionListener(CellSelectionListener l) {
        this.cellSelectionListener = l;
    }

    private void notifyCellSelectionChanged() {
        if (cellSelectionListener != null) cellSelectionListener.onCellSelectionChanged(this);
    }

    public TL_iv.pageTableCell findCellAt(int localX, int localY) {
        if (model == null) return null;
        int gx = localX - scrollView.getLeft() - scrollContent.getLeft() - grid.getLeft() + scrollView.getScrollX();
        int gy = localY - scrollView.getTop() - scrollContent.getTop() - grid.getTop();
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            if (gx >= host.getLeft() && gx < host.getRight()
                && gy >= host.getTop() && gy < host.getBottom()) {
                return host.cell;
            }
        }
        return null;
    }

    private int gridX(int localX) {
        return localX - scrollView.getLeft() - scrollContent.getLeft() - grid.getLeft() + scrollView.getScrollX();
    }

    private int gridY(int localY) {
        return localY - scrollView.getTop() - scrollContent.getTop() - grid.getTop();
    }

    public int findRowHandleAt(int localX, int localY) {
        if (model == null) return -1;
        return grid.rowHandleAtGrid(gridX(localX), gridY(localY));
    }

    public int findColHandleAt(int localX, int localY) {
        if (model == null) return -1;
        return grid.colHandleAtGrid(gridX(localX), gridY(localY));
    }

    public void selectWholeRow(int r) {
        if (model == null || r < 0 || r >= model.rowCount) return;
        selectedCells.clear();
        for (int c = 0; c < model.colCount; c++) {
            TL_iv.pageTableCell cell = model.grid[r][c];
            if (cell != null) selectedCells.add(cell);
        }
        grid.invalidate();
        notifyCellSelectionChanged();
    }

    public void selectWholeColumn(int c) {
        if (model == null || c < 0 || c >= model.colCount) return;
        selectedCells.clear();
        for (int r = 0; r < model.rowCount; r++) {
            TL_iv.pageTableCell cell = model.grid[r][c];
            if (cell != null) selectedCells.add(cell);
        }
        grid.invalidate();
        notifyCellSelectionChanged();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int width = MeasureSpec.getSize(widthMeasureSpec);
        final int endInset = RichBlockChrome.insetEndFor(currentRow);
        final int avail = Math.max(0, width - blockInset() - endInset);
        final int titleWidth = Math.max(0, avail - 2 * dp(16));
        titleEditText.measure(
            MeasureSpec.makeMeasureSpec(titleWidth, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        final int titleH = titleEditText.getMeasuredHeight();
        scrollView.measure(
            MeasureSpec.makeMeasureSpec(avail, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
        );
        // Field-mode cell: apply the quote's vertical edge padding here (the base onBlockInsetChanged override
        // doesn't, since we lay out manually), so a table inside a quote sits inside its background.
        final int qEdge = RichBlockChrome.quoteTopPad(currentRow) + RichBlockChrome.quoteBottomPad(currentRow);
        setMeasuredDimension(width, qEdge + titleH + dp(2) + scrollView.getMeasuredHeight());
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        final int width = right - left;
        final int startInset = blockInset();
        final int endInset = RichBlockChrome.insetEndFor(currentRow);
        final int insLeft = blockRtl ? endInset : startInset;
        final int insRight = blockRtl ? startInset : endInset;
        final int titleH = titleEditText.getMeasuredHeight();
        final int qTop = RichBlockChrome.quoteTopPad(currentRow); // push content down to sit inside the quote bg
        titleEditText.layout(insLeft + dp(16), qTop, Math.max(insLeft + dp(16), width - insRight - dp(16)), qTop + titleH);
        final int scrollTop = qTop + titleH + dp(2);
        scrollView.layout(insLeft, scrollTop, width - insRight, scrollTop + scrollView.getMeasuredHeight());
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        getViewTreeObserver().addOnGlobalFocusChangeListener(focusInvalidator);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnGlobalFocusChangeListener(focusInvalidator);
        super.onDetachedFromWindow();
    }

    private final ViewTreeObserver.OnGlobalFocusChangeListener focusInvalidator =
        (oldFocus, newFocus) -> invalidateGridForFocus();

    private void invalidateGridForFocus() {
        if (grid != null) grid.invalidate();
    }

    public boolean isPressOnText(int localX, int localY) {
        TL_iv.pageTableCell cell = findCellAt(localX, localY);
        if (cell == null) return false;
        RichTableCellHost host = grid.hostForAnchor(cell);
        if (host == null) return false;
        int gx = localX - scrollView.getLeft() - scrollContent.getLeft() - grid.getLeft() + scrollView.getScrollX();
        int gy = localY - scrollView.getTop() - scrollContent.getTop() - grid.getTop();
        int hx = gx - host.getLeft() - host.editText.getLeft();
        int hy = gy - host.getTop() - host.editText.getTop();
        Layout layout = host.editText.getLayout();
        if (layout == null) return false;
        int textY = hy - host.editText.getPaddingTop();
        int textX = hx - host.editText.getPaddingLeft();
        if (textY < 0 || textY >= layout.getHeight()) return false;
        int line = layout.getLineForVertical(textY);
        if (line < 0 || line >= layout.getLineCount()) return false;
        float lineLeft = layout.getLineLeft(line);
        float lineRight = layout.getLineRight(line);
        return textX >= lineLeft && textX <= lineRight;
    }

    public void applyHeaderToggle(boolean header) {
        for (TL_iv.pageTableCell c : selectedCells) {
            TableModel.setHeader(c, header);
            RichTableCellHost host = grid.hostForAnchor(c);
            if (host != null) host.refreshFromCell();
        }
        grid.invalidate();
        if (delegate != null && currentRow != null) delegate.onTextChanged(currentRow);
    }

    public void applyHorizontalAlign(int align) {
        for (TL_iv.pageTableCell c : selectedCells) {
            TableModel.setAlign(c, align);
            RichTableCellHost host = grid.hostForAnchor(c);
            if (host != null) host.refreshFromCell();
        }
        grid.invalidate();
        if (delegate != null && currentRow != null) delegate.onTextChanged(currentRow);
    }

    public void applyVerticalAlign(int valign) {
        for (TL_iv.pageTableCell c : selectedCells) {
            TableModel.setVAlign(c, valign);
            RichTableCellHost host = grid.hostForAnchor(c);
            if (host != null) host.refreshFromCell();
        }
        grid.invalidate();
        if (delegate != null && currentRow != null) delegate.onTextChanged(currentRow);
    }

    public int commonHorizontalAlign() {
        int common = -1;
        for (TL_iv.pageTableCell c : selectedCells) {
            int a = TableModel.alignOf(c);
            if (common == -1) common = a;
            else if (common != a) return -1;
        }
        return common;
    }

    public int commonVerticalAlign() {
        int common = -1;
        for (TL_iv.pageTableCell c : selectedCells) {
            int a = TableModel.valignOf(c);
            if (common == -1) common = a;
            else if (common != a) return -1;
        }
        return common;
    }

    public void applyDeleteToSelectedCells() {
        for (TL_iv.pageTableCell c : selectedCells) {
            TableModel.applyPlainText(c, "");
            RichTableCellHost host = grid.hostForAnchor(c);
            if (host != null) host.editText.setTextSilently("");
        }
        if (delegate != null && currentRow != null) delegate.onTextChanged(currentRow);
    }

    public void refreshAfterModelChange() {
        grid.rebindAfterModelChange();
        wireCellListeners();
        if (delegate != null && currentRow != null) delegate.onTextChanged(currentRow);
    }

    public boolean applyMergeFromSelection() {
        if (model == null || selectedCells.size() < 2) return false;
        java.util.HashSet<TL_iv.pageTableCell> snapshot = new java.util.HashSet<>(selectedCells);
        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE;
        for (TL_iv.pageTableCell c : snapshot) {
            minR = Math.min(minR, model.anchorRowOf(c));
            minC = Math.min(minC, model.anchorColOf(c));
        }
        selectedCells.clear();
        boolean ok = model.mergeCells(snapshot);
        if (ok) {
            refreshAfterModelChange();
            grid.invalidate();
            focusCellAt(minR, minC);
            notifyCellSelectionChanged();
        } else {
            selectedCells.addAll(snapshot);
        }
        return ok;
    }

    private TL_iv.pageTableCell snapshotTopLeft(java.util.Set<TL_iv.pageTableCell> sel) {
        TL_iv.pageTableCell best = null;
        int bestR = Integer.MAX_VALUE, bestC = Integer.MAX_VALUE;
        for (TL_iv.pageTableCell c : sel) {
            int r = model.anchorRowOf(c), col = model.anchorColOf(c);
            if (r < bestR || (r == bestR && col < bestC)) {
                bestR = r; bestC = col; best = c;
            }
        }
        return best;
    }

    public boolean applyUnmergeFromSelection() {
        if (model == null || selectedCells.size() != 1) return false;
        TL_iv.pageTableCell anchor = selectedCells.iterator().next();
        if (TableModel.spanCol(anchor) <= 1 && TableModel.spanRow(anchor) <= 1) return false;
        final int ar = model.anchorRowOf(anchor), ac = model.anchorColOf(anchor);
        selectedCells.clear();
        boolean ok = model.unmergeCell(anchor);
        if (ok) {
            refreshAfterModelChange();
            grid.invalidate();
            focusCellAt(ar, ac);
            notifyCellSelectionChanged();
        } else {
            selectedCells.add(anchor);
        }
        return ok;
    }

    public boolean applyDeleteRowsFromSelection() {
        if (model == null || selectedCells.isEmpty()) return false;
        java.util.HashSet<Integer> rows = new java.util.HashSet<>();
        int firstRow = Integer.MAX_VALUE;
        for (TL_iv.pageTableCell c : selectedCells) {
            int r = model.anchorRowOf(c);
            rows.add(r);
            firstRow = Math.min(firstRow, r);
        }
        selectedCells.clear();
        boolean ok = model.deleteRows(rows);
        refreshAfterModelChange();
        if (ok) focusCellAt(firstRow, 0);
        return ok;
    }

    public boolean applyDeleteColumnsFromSelection() {
        if (model == null || selectedCells.isEmpty()) return false;
        java.util.HashSet<Integer> cols = new java.util.HashSet<>();
        int firstCol = Integer.MAX_VALUE;
        for (TL_iv.pageTableCell c : selectedCells) {
            int col = model.anchorColOf(c);
            cols.add(col);
            firstCol = Math.min(firstCol, col);
        }
        selectedCells.clear();
        boolean ok = model.deleteColumns(cols);
        refreshAfterModelChange();
        if (ok) focusCellAt(0, firstCol);
        return ok;
    }

    public boolean applyInsertRowFromSelection(boolean above) {
        if (model == null || selectedCells.isEmpty()) return false;
        int idx;
        if (above) {
            idx = Integer.MAX_VALUE;
            for (TL_iv.pageTableCell c : selectedCells) {
                idx = Math.min(idx, model.anchorRowOf(c));
            }
        } else {
            idx = 0;
            for (TL_iv.pageTableCell c : selectedCells) {
                idx = Math.max(idx, model.anchorRowOf(c) + TableModel.spanRow(c));
            }
        }
        selectedCells.clear();
        boolean ok = model.insertRowAt(idx);
        refreshAfterModelChange();
        if (ok) focusCellAt(idx, 0);
        notifyCellSelectionChanged();
        return ok;
    }

    public boolean applyInsertColumnFromSelection(boolean left) {
        if (model == null || selectedCells.isEmpty()) return false;
        int idx;
        if (left) {
            idx = Integer.MAX_VALUE;
            for (TL_iv.pageTableCell c : selectedCells) {
                idx = Math.min(idx, model.anchorColOf(c));
            }
        } else {
            idx = 0;
            for (TL_iv.pageTableCell c : selectedCells) {
                idx = Math.max(idx, model.anchorColOf(c) + TableModel.spanCol(c));
            }
        }
        selectedCells.clear();
        boolean ok = model.insertColumnAt(idx);
        refreshAfterModelChange();
        if (ok) focusCellAt(0, idx);
        notifyCellSelectionChanged();
        return ok;
    }

    // Entry point for arrow navigation crossing into the table from a neighbouring block:
    // focus the title (caret at start) when entering from above, the last cell (caret at end)
    // when entering from below.
    public boolean focusEdgeCell(boolean last) {
        if (model == null) return false;
        if (!last) {
            titleEditText.requestEditFocus();
            titleEditText.setSelection(0);
            return true;
        }
        if (model.anchors().isEmpty()) return false;
        final TL_iv.pageTableCell anchor = model.anchors().get(model.anchors().size() - 1);
        final RichTableCellHost host = grid.hostForAnchor(anchor);
        if (host == null) return false;
        host.editText.requestEditFocus();
        host.editText.setSelection(host.editText.length());
        return true;
    }

    // Move from the title down into the first cell (used by Tab / arrow-down out of the title).
    public boolean focusFirstCell() {
        if (model == null || model.anchors().isEmpty()) return false;
        final RichTableCellHost host = grid.hostForAnchor(model.anchors().get(0));
        if (host == null) return false;
        host.editText.requestEditFocus();
        host.editText.setSelection(0);
        return true;
    }

    private void focusCellAt(int r, int c) {
        if (model == null || model.rowCount == 0 || model.colCount == 0) return;
        final int rr = Math.max(0, Math.min(r, model.rowCount - 1));
        final int cc = Math.max(0, Math.min(c, model.colCount - 1));
        final TL_iv.pageTableCell anchor = model.grid[rr][cc];
        if (anchor == null) return;
        post(() -> {
            RichTableCellHost host = grid.hostForAnchor(anchor);
            if (host == null) return;
            host.editText.requestEditFocus();
            host.editText.setSelection(host.editText.length());
        });
    }

    public boolean isEmpty() {
        return model == null || model.rowCount == 0 || model.colCount == 0;
    }

    public boolean allSelectedHeader() {
        if (selectedCells.isEmpty()) return false;
        for (TL_iv.pageTableCell c : selectedCells) if (!c.header) return false;
        return true;
    }

    public BlockRow getRow() {
        return currentRow;
    }

    public TableModel getModel() {
        return model;
    }

    public RichTableCellGrid getGrid() {
        return grid;
    }

    public void setLocked(boolean locked) {
        titleEditText.setLocked(locked);
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof RichTableCellHost) ((RichTableCellHost) child).setLocked(locked);
        }
    }

    public void hideActionModes() {
        titleEditText.hideActionMode();
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof RichTableCellHost) ((RichTableCellHost) child).editText.hideActionMode();
        }
    }

    private void wireCellListeners() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            final RichTableCellHost host = (RichTableCellHost) child;
            host.editText.setListener(new RichEditText.Listener() {
                @Override
                public void onTextWillChange(RichEditText et, int removed, int added) {
                    if (delegate != null && currentRow != null) delegate.onTextWillChange(currentRow, removed, added);
                }

                @Override
                public void onTextChanged(RichEditText et, Editable text) {
                    if (host.cell != null) {
                        TableModel.applyStyledText(host.cell, text);
                    }
                    if (delegate != null && currentRow != null) {
                        delegate.onTextChanged(currentRow);
                    }
                }

                @Override
                public boolean onTab(RichEditText et, boolean shift) {
                    return moveFocusByTab(host, shift);
                }

                @Override
                public void onRequestWindowFocusable(RichEditText et, boolean showKeyboard) {
                    if (delegate != null) delegate.onRequestWindowFocusable(et, showKeyboard);
                }

                @Override
                public void onLockedInsert(RichEditText et, CharSequence text) {
                    if (delegate != null) delegate.onLockedInsert(text);
                }

                @Override
                public boolean onSelectAll(RichEditText et) {
                    if (delegate != null && currentRow != null) return delegate.onSelectAll(currentRow);
                    return false;
                }

                @Override
                public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                    if (hijackingSelection || selStart == selEnd || delegate == null) return;
                    final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                    if (helper == null) return;
                    if (helper.isInSelectionMode() && helper.getSelectedCell() == RichTableCell.this) return;
                    final int s = selStart, e = selEnd;
                    final int childPos = childPosForAnchor(host.cell);
                    if (childPos < 0) return;
                    post(() -> {
                        if (et.length() < e || et.getSelectionStart() == et.getSelectionEnd()) return;
                        if (helper.selectRangeOf(RichTableCell.this, childPos, s, e)) {
                            hijackingSelection = true;
                            et.setSelection(e);
                            hijackingSelection = false;
                        }
                    });
                }
            });
            host.editText.setDelegate(() -> {
                if (host.cell != null) {
                    TableModel.applyStyledText(host.cell, host.editText.getText());
                }
                if (delegate != null && currentRow != null) {
                    delegate.onSpansChanged(currentRow);
                }
            });
        }
    }

    public boolean moveFocusByTab(RichTableCellHost from, boolean shift) {
        if (model == null) return false;
        int idx = model.anchors().indexOf(from.cell);
        if (idx < 0) return false;
        int next = shift ? idx - 1 : idx + 1;
        if (next < 0 || next >= model.anchors().size()) return false;
        RichTableCellHost target = grid.hostForAnchor(model.anchors().get(next));
        if (target == null) return false;
        target.editText.requestEditFocus();
        target.editText.setSelection(target.editText.length());
        return true;
    }

    public RichTableCellHost findHostContaining(View focused) {
        if (focused == null) return null;
        android.view.ViewParent p = focused.getParent();
        while (p != null) {
            if (p instanceof RichTableCellHost) return (RichTableCellHost) p;
            if (p == this) return null;
            p = p.getParent();
        }
        return null;
    }

    @Override
    public void updateColors() {
        titleEditText.updateColors();
        final int blackText = Theme.getColor(Theme.key_windowBackgroundWhiteBlackText, resourcesProvider);
        titleEditText.setTextColor(blackText);
        titleEditText.setHintTextColor(Theme.multAlpha(blackText, 0.35f));
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof RichTableCellHost) ((RichTableCellHost) child).editText.updateColors();
        }
        grid.applyColors();
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        if (model == null) return;
        // Child 0 is the title (drawn above the grid); cell anchors follow as 1..N. The order here
        // defines the child positions the selection helper uses, so the title must come first.
        final Layout titleLayout = titleEditText.getLayout();
        if (titleLayout != null) {
            final int titleX = titleEditText.getLeft() + titleEditText.getPaddingLeft();
            final int titleY = titleEditText.getTop() + titleEditText.getPaddingTop();
            out.add(new TextSelectionHelper.TextLayoutBlock() {
                @Override public Layout getLayout() { return titleLayout; }
                @Override public int getX() { return titleX; }
                @Override public int getY() { return titleY; }
                @Override public int getRow() { return 0; }
                @Override public CharSequence getText() {
                    return currentRow != null && currentRow.block instanceof TL_iv.pageBlockTable
                        && ((TL_iv.pageBlockTable) currentRow.block).title != null
                        ? RichTextStyle.toSpannable(((TL_iv.pageBlockTable) currentRow.block).title) : "";
                }
            });
        }
        for (int i = 0, n = model.anchors().size(); i < n; i++) {
            TL_iv.pageTableCell cell = model.anchors().get(i);
            RichTableCellHost host = grid.hostForAnchor(cell);
            if (host == null) continue;
            final Layout layout = host.editText.getLayout();
            if (layout == null) continue;
            final int textX = scrollView.getLeft() + scrollContent.getLeft() + grid.getLeft() - scrollView.getScrollX()
                + host.getLeft() + host.editText.getLeft() + host.editText.getPaddingLeft();
            final int textY = scrollView.getTop() + scrollContent.getTop() + grid.getTop()
                + host.getTop() + host.editText.getTop() + host.editText.getPaddingTop();
            final int rowIndex = model.anchorRowOf(cell) + 10;
            final TL_iv.pageTableCell textCell = cell;
            out.add(new TextSelectionHelper.TextLayoutBlock() {
                @Override public Layout getLayout() { return layout; }
                @Override public int getX() { return textX; }
                @Override public int getY() { return textY; }
                @Override public int getRow() { return rowIndex; }
                @Override public CharSequence getText() { return TableModel.readStyledText(textCell); }
            });
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        super.dispatchDraw(canvas);
        if (model == null) return;
        TextSelectionHelper.ArticleTextSelectionHelper helper = delegate != null ? delegate.getSelectionHelper() : null;
        if (helper == null) return;
        tmpBlocks.clear();
        fillTextLayoutBlocks(tmpBlocks);
        for (int i = 0; i < tmpBlocks.size(); i++) {
            TextSelectionHelper.TextLayoutBlock b = tmpBlocks.get(i);
            canvas.save();
            canvas.translate(b.getX(), b.getY());
            helper.draw(canvas, this, i);
            canvas.restore();
        }
    }

    @Override
    public void invalidate() {
        super.invalidate();
        if (grid != null) grid.invalidate();
    }

    public static final class Factory extends UItem.UItemFactory<RichTableCell> {
        static { setup(new Factory()); }

        @Override
        public RichTableCell createView(Context context, RecyclerListView listView, int currentAccount, int classGuid, Theme.ResourcesProvider resourcesProvider) {
            final RichTableCell cell = new RichTableCell(context, resourcesProvider);
            cell.setBackground(new RichEditor.DraggingDrawable(Theme.getColor(Theme.key_windowBackgroundWhite, resourcesProvider)));
            return cell;
        }

        @Override
        public void bindView(View view, UItem item, boolean divider, UniversalAdapter adapter, UniversalRecyclerView listView) {
            final RichTableCell cell = (RichTableCell) view;
            final BlockRow row = (BlockRow) item.object;
            final Delegate delegate = (Delegate) item.object2;
            cell.bind(row, delegate);
        }

        public static UItem of(BlockRow row, Delegate delegate) {
            final UItem item = UItem.ofFactory(Factory.class);
            item.object = row;
            item.object2 = delegate;
            return item;
        }

        @Override
        public boolean isClickable() {
            return false;
        }
    }
}
