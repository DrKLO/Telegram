package org.Tajgram.ui.iv;

import static org.Tajgram.messenger.AndroidUtilities.dp;

import android.content.Context;
import android.graphics.Canvas;
import android.text.Editable;
import android.text.Layout;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.TextView;

import org.Tajgram.messenger.AndroidUtilities;

import org.Tajgram.tgnet.tl.TL_iv;
import org.Tajgram.ui.ActionBar.Theme;
import org.Tajgram.ui.Cells.TextSelectionHelper;
import org.Tajgram.ui.Components.LayoutHelper;
import org.Tajgram.ui.Components.RecyclerListView;
import org.Tajgram.ui.Components.UItem;
import org.Tajgram.ui.Components.UniversalAdapter;
import org.Tajgram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;
import java.util.Set;

public class RichTableCell extends FrameLayout implements Theme.Colorable, TextSelectionHelper.ArticleSelectableView {

    public interface Delegate {
        void onTextChanged(BlockRow row);
        TextSelectionHelper.ArticleTextSelectionHelper getSelectionHelper();
        default void onRequestWindowFocusable(RichEditText editText, boolean showKeyboard) {}
    }

    private static final int ADD_BTN_DP = 32;

    private final Theme.ResourcesProvider resourcesProvider;
    private final HorizontalScrollView scrollView;
    private final RichTableCellGrid grid;
    private final ScrollContent scrollContent;
    private final TextView addRowButton;
    private final TextView addColumnButton;
    private final ArrayList<TextSelectionHelper.TextLayoutBlock> tmpBlocks = new ArrayList<>();

    private BlockRow currentRow;
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
        scrollView.setPadding(dp(16), 0, dp(16), 0);
        addView(scrollView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.TOP, 0, 6, 0, 6));

        grid = new RichTableCellGrid(context, resourcesProvider);
        addRowButton = makeAddButton(context, v -> addRow());
        addColumnButton = makeAddButton(context, v -> addColumn());

        scrollContent = new ScrollContent(context);
        scrollContent.addView(grid);
        scrollContent.addView(addColumnButton);
        scrollContent.addView(addRowButton);
        scrollView.addView(scrollContent, new HorizontalScrollView.LayoutParams(HorizontalScrollView.LayoutParams.WRAP_CONTENT, HorizontalScrollView.LayoutParams.WRAP_CONTENT));

        setWillNotDraw(false);
    }

    private TextView makeAddButton(Context context, View.OnClickListener click) {
        TextView tv = new TextView(context);
        tv.setText("+");
        tv.setGravity(Gravity.CENTER);
        tv.setIncludeFontPadding(false);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 20);
        tv.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText, resourcesProvider));
        tv.setBackground(Theme.createSelectorDrawable(Theme.getColor(Theme.key_listSelector, resourcesProvider), 2));
        tv.setOnClickListener(click);
        return tv;
    }

    private final class ScrollContent extends ViewGroup {
        ScrollContent(Context ctx) {
            super(ctx);
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            int addBtn = AndroidUtilities.dp(ADD_BTN_DP);
            int parentW = MeasureSpec.getSize(widthMeasureSpec);
            int availableForGrid = Math.max(0, parentW - addBtn);
            grid.measure(MeasureSpec.makeMeasureSpec(availableForGrid, MeasureSpec.AT_MOST), heightMeasureSpec);
            int gridW = grid.getMeasuredWidth();
            int gridH = grid.getMeasuredHeight();
            addColumnButton.measure(
                MeasureSpec.makeMeasureSpec(addBtn, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(gridH, MeasureSpec.EXACTLY));
            addRowButton.measure(
                MeasureSpec.makeMeasureSpec(gridW, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(addBtn, MeasureSpec.EXACTLY));
            setMeasuredDimension(gridW + addBtn, gridH + addBtn);
        }

        @Override
        protected void onLayout(boolean changed, int l, int t, int r, int b) {
            int gridW = grid.getMeasuredWidth();
            int gridH = grid.getMeasuredHeight();
            grid.layout(0, 0, gridW, gridH);
            addColumnButton.layout(gridW, 0, gridW + addColumnButton.getMeasuredWidth(), gridH);
            addRowButton.layout(0, gridH, gridW, gridH + addRowButton.getMeasuredHeight());
        }
    }

    public void bind(BlockRow row, Delegate delegate) {
        this.currentRow = row;
        this.delegate = delegate;
        if (!(row.block instanceof TL_iv.pageBlockTable)) return;
        this.model = new TableModel((TL_iv.pageBlockTable) row.block);
        grid.setModel(model);
        grid.setSelectionProvider(selectedCells::contains);
        wireCellListeners();
        updateColors();
        scrollContent.requestLayout();
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
        int gx = localX - scrollView.getLeft() - grid.getLeft() + scrollView.getScrollX();
        int gy = localY - scrollView.getTop() - grid.getTop();
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

    public boolean isPressOnText(int localX, int localY) {
        TL_iv.pageTableCell cell = findCellAt(localX, localY);
        if (cell == null) return false;
        RichTableCellHost host = grid.hostForAnchor(cell);
        if (host == null) return false;
        int gx = localX - scrollView.getLeft() - grid.getLeft() + scrollView.getScrollX();
        int gy = localY - scrollView.getTop() - grid.getTop();
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
        selectedCells.clear();
        boolean ok = model.mergeCells(snapshot);
        if (ok) {
            refreshAfterModelChange();
            grid.invalidate();
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
        selectedCells.clear();
        boolean ok = model.unmergeCell(anchor);
        if (ok) {
            refreshAfterModelChange();
            grid.invalidate();
            notifyCellSelectionChanged();
        } else {
            selectedCells.add(anchor);
        }
        return ok;
    }

    public boolean applyDeleteRowsFromSelection() {
        if (model == null || selectedCells.isEmpty()) return false;
        java.util.HashSet<Integer> rows = new java.util.HashSet<>();
        for (TL_iv.pageTableCell c : selectedCells) rows.add(model.anchorRowOf(c));
        selectedCells.clear();
        boolean ok = model.deleteRows(rows);
        refreshAfterModelChange();
        return ok;
    }

    public boolean applyDeleteColumnsFromSelection() {
        if (model == null || selectedCells.isEmpty()) return false;
        java.util.HashSet<Integer> cols = new java.util.HashSet<>();
        for (TL_iv.pageTableCell c : selectedCells) cols.add(model.anchorColOf(c));
        selectedCells.clear();
        boolean ok = model.deleteColumns(cols);
        refreshAfterModelChange();
        return ok;
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
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof RichTableCellHost) ((RichTableCellHost) child).setLocked(locked);
        }
    }

    private void wireCellListeners() {
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            final RichTableCellHost host = (RichTableCellHost) child;
            host.editText.setListener(new RichEditText.Listener() {
                @Override
                public void onTextChanged(RichEditText et, Editable text) {
                    if (host.cell != null) {
                        TableModel.applyPlainText(host.cell, text.toString());
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
                public void onSelectionChanged(RichEditText et, int selStart, int selEnd) {
                    if (hijackingSelection || selStart == selEnd || delegate == null) return;
                    final TextSelectionHelper.ArticleTextSelectionHelper helper = delegate.getSelectionHelper();
                    if (helper == null) return;
                    if (helper.isInSelectionMode() && helper.getSelectedCell() == RichTableCell.this) return;
                    final int s = selStart, e = selEnd;
                    final int childPos = model == null ? 0 : model.flatIndexOfAnchor(host.cell);
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
        for (int i = 0; i < grid.getChildCount(); i++) {
            View child = grid.getChildAt(i);
            if (child instanceof RichTableCellHost) ((RichTableCellHost) child).editText.applyColors();
        }
        grid.applyColors();
    }

    @Override
    public void fillTextLayoutBlocks(ArrayList<TextSelectionHelper.TextLayoutBlock> out) {
        if (model == null) return;
        for (int i = 0, n = model.anchors().size(); i < n; i++) {
            TL_iv.pageTableCell cell = model.anchors().get(i);
            RichTableCellHost host = grid.hostForAnchor(cell);
            if (host == null) continue;
            final Layout layout = host.editText.getLayout();
            if (layout == null) continue;
            final int textX = scrollView.getLeft() + grid.getLeft() - scrollView.getScrollX()
                + host.getLeft() + host.editText.getLeft() + host.editText.getPaddingLeft();
            final int textY = scrollView.getTop() + grid.getTop()
                + host.getTop() + host.editText.getTop() + host.editText.getPaddingTop();
            final int rowIndex = model.anchorRowOf(cell) + 10;
            out.add(new TextSelectionHelper.TextLayoutBlock() {
                @Override public Layout getLayout() { return layout; }
                @Override public int getX() { return textX; }
                @Override public int getY() { return textY; }
                @Override public int getRow() { return rowIndex; }
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
            return new RichTableCell(context, resourcesProvider);
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
