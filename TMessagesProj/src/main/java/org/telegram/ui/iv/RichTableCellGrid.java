package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.dpf2;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import org.telegram.tgnet.tl.TL_iv;
import org.telegram.ui.Components.AnimatedFloat;
import org.telegram.ui.Components.CubicBezierInterpolator;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.ActionBar.Theme;

public class RichTableCellGrid extends ViewGroup {

    private TableModel model;
    private Theme.ResourcesProvider resourcesProvider;

    private int[] colWidths = new int[0];
    private int[] rowHeights = new int[0];
    private int[] colStarts = new int[0];
    private int[] rowStarts = new int[0];

    public interface CellSelectionProvider {
        boolean isSelected(TL_iv.pageTableCell cell);
    }

    private CellSelectionProvider selectionProvider;

    public void setSelectionProvider(CellSelectionProvider provider) {
        this.selectionProvider = provider;
        invalidate();
    }

    private final Paint linePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint headerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stripPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint selectedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint bulgeFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF selRect = new RectF();
    private final RectF arcRect = new RectF();
    private final android.graphics.Path bulgePath = new android.graphics.Path();

    private AnimatedFloat selectionFade;
    private int selectedFillBaseAlpha;
    private int selectedStrokeBaseAlpha;

    private boolean leftBulge, bottomBulge;
    private int bulgeRowTop, bulgeRowBot, bulgeColLeft, bulgeColRight;
    private int dotColor, dotOnSelectionColor;

    private static final int MIN_COL_DP = 50;
    private static final int COMPACT_MIN_COL_DP = 20;
    private static final int CELL_HORIZONTAL_PADDING_DP = 12;
    public static final int GRID_PADDING_DP = 4;
    public static final int HANDLE_PAD_DP = 20;
    private static final int DOT_AWAY_DP = 6;
    private static final int BULGE_DP = 16;
    private static final int BULGE_RADIUS_DP = 10;

    public RichTableCellGrid(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
        linePaint.setStyle(Paint.Style.STROKE);
        linePaint.setStrokeWidth(dpf2(0.66f));
        selectedStrokePaint.setStyle(Paint.Style.STROKE);
        selectedStrokePaint.setStrokeWidth(dp(2));
        selectedStrokePaint.setStrokeJoin(Paint.Join.ROUND);
        selectedStrokePaint.setStrokeCap(Paint.Cap.ROUND);

        selectionFade = new AnimatedFloat(this, 0L, 220L, CubicBezierInterpolator.EASE_OUT_QUINT);

        applyColors();
    }

    public void applyColors() {
        linePaint.setColor(Theme.getColor(Theme.key_table_border, resourcesProvider));
        int tint = Theme.getColor(Theme.key_switchTrack, resourcesProvider);
        int r = Color.red(tint);
        int g = Color.green(tint);
        int b = Color.blue(tint);
        headerPaint.setColor(Theme.getColor(Theme.key_table_background, resourcesProvider));
        stripPaint.setColor(Color.argb(20, r, g, b));
        selectedFillBaseAlpha = 80;
        selectedStrokeBaseAlpha = 0xFF;
        selectedStrokePaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        dotPaint.setStyle(Paint.Style.FILL);
        dotColor = Theme.getColor(Theme.key_windowBackgroundWhiteGrayText7, resourcesProvider);
        dotOnSelectionColor = Theme.getColor(Theme.key_featuredStickers_buttonText, resourcesProvider);
        dotPaint.setColor(dotColor);
        bulgeFillPaint.setStyle(Paint.Style.FILL);
        bulgeFillPaint.setColor(Theme.getColor(Theme.key_featuredStickers_addButton, resourcesProvider));
        invalidate();
    }

    public void setModel(TableModel model) {
        this.model = model;
        rebuildHosts();
    }

    public TableModel getModel() {
        return model;
    }

    public RichTableCellHost hostForAnchor(TL_iv.pageTableCell anchor) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof RichTableCellHost && ((RichTableCellHost) child).cell == anchor) {
                return (RichTableCellHost) child;
            }
        }
        return null;
    }

    private void rebuildHosts() {
        for (int i = getChildCount() - 1; i >= 0; i--) {
            View child = getChildAt(i);
            if (child instanceof RichTableCellHost) removeViewAt(i);
        }
        if (model == null) return;
        for (int i = 0, n = model.anchors().size(); i < n; i++) {
            final TL_iv.pageTableCell cell = model.anchors().get(i);
            final RichTableCellHost host = new RichTableCellHost(getContext(), resourcesProvider);
            host.setCompact(model.block.compact);
            host.bind(cell);
            addView(host);
        }
    }

    public void rebindAfterModelChange() {
        rebuildHosts();
        requestLayout();
        invalidate();
    }

    public void refreshCompact() {
        if (model == null) return;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child instanceof RichTableCellHost) {
                ((RichTableCellHost) child).setCompact(model.block.compact);
            }
        }
        requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final int padL = dp(HANDLE_PAD_DP);
        final int padT = dp(GRID_PADDING_DP);
        final int padR = dp(GRID_PADDING_DP);
        // Keep a small gap between the table and the following block while allowing the
        // bottom handles to extend beyond the grid's measured bounds.
        final int padB = dp(10);
        if (model == null || model.rowCount == 0 || model.colCount == 0) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), padT + padB);
            colWidths = new int[0];
            rowHeights = new int[0];
            colStarts = new int[0];
            rowStarts = new int[0];
            return;
        }

        final int parentWidth = MeasureSpec.getSize(widthMeasureSpec) - padL - padR;
        final int rowCount = model.rowCount;
        final int colCount = model.colCount;

        colWidths = new int[colCount];
        rowHeights = new int[rowCount];

        final int minColWidth = dp(model.block.compact ? COMPACT_MIN_COL_DP : MIN_COL_DP);
        // Match RichMessageLayout's table behavior: size columns from their content, but cap
        // each one so very long text wraps after the table has grown to a useful width.
        final int cellHorizontalPaddingDp = model.block.compact ? 5 : CELL_HORIZONTAL_PADDING_DP;
        final int maxTextWidth = colCount == 2
            ? Math.max(0, parentWidth / 2 - dp(cellHorizontalPaddingDp * 4))
            : Math.max(0, Math.round(parentWidth / 1.5f));
        final int maxColWidth = Math.max(minColWidth, maxTextWidth + dp(cellHorizontalPaddingDp * 2));
        for (int c = 0; c < colCount; c++) colWidths[c] = minColWidth;
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            final RichTableCellHost host = (RichTableCellHost) child;
            if (TableModel.spanCol(host.cell) != 1) continue;
            final int column = model.anchorColOf(host.cell);
            if (column < 0 || column >= colCount) continue;
            final int desired = Math.round(Layout.getDesiredWidth(host.editText.getText(), host.editText.getPaint()))
                + dp(cellHorizontalPaddingDp * 2);
            colWidths[column] = Math.max(colWidths[column], Math.min(maxColWidth, desired));
        }
        // A merged cell can also make its covered columns wider. Apply those constraints after
        // single-column cells establish the natural width of each individual column.
        for (int i = 0; i < getChildCount(); i++) {
            final View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            final RichTableCellHost host = (RichTableCellHost) child;
            final int span = TableModel.spanCol(host.cell);
            if (span <= 1) continue;
            final int column = model.anchorColOf(host.cell);
            final int end = Math.min(colCount, column + span);
            if (column < 0 || column >= end) continue;
            int coveredWidth = 0;
            for (int c = column; c < end; c++) coveredWidth += colWidths[c];
            final int desired = Math.min(maxColWidth * (end - column),
                Math.round(Layout.getDesiredWidth(host.editText.getText(), host.editText.getPaint()))
                    + dp(cellHorizontalPaddingDp * 2));
            int deficit = desired - coveredWidth;
            for (int c = column; c < end && deficit > 0; c++) {
                final int columnsLeft = end - c;
                final int extra = (deficit + columnsLeft - 1) / columnsLeft;
                colWidths[c] += extra;
                deficit -= extra;
            }
        }

        int intrinsicWidth = 0;
        for (int width : colWidths) intrinsicWidth += width;
        if (intrinsicWidth < parentWidth && colCount > 0) {
            int remaining = parentWidth - intrinsicWidth;
            int remainingWeight = intrinsicWidth;
            for (int c = 0; c < colCount; c++) {
                final int extra = c == colCount - 1
                    ? remaining
                    : Math.round(remaining * colWidths[c] / (float) remainingWeight);
                colWidths[c] += extra;
                remaining -= extra;
                remainingWeight -= colWidths[c] - extra;
            }
        }

        int hostUnspec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED);

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            int cs = TableModel.spanCol(host.cell);

            int hostWidth = 0;
            for (int cc = aC; cc < aC + cs && cc < colCount; cc++) hostWidth += colWidths[cc];

            int spec = MeasureSpec.makeMeasureSpec(hostWidth, MeasureSpec.EXACTLY);
            host.measure(spec, hostUnspec);

            int rs = TableModel.spanRow(host.cell);
            if (rs == 1) {
                if (host.getMeasuredHeight() > rowHeights[aR]) {
                    rowHeights[aR] = host.getMeasuredHeight();
                }
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int rs = TableModel.spanRow(host.cell);
            if (rs <= 1) continue;
            int totalRows = 0;
            for (int rr = aR; rr < aR + rs && rr < rowCount; rr++) totalRows += rowHeights[rr];
            int needed = host.getMeasuredHeight();
            if (needed > totalRows) {
                int deficit = needed - totalRows;
                int per = deficit / Math.max(rs, 1);
                int rem = deficit % Math.max(rs, 1);
                for (int rr = aR; rr < aR + rs && rr < rowCount; rr++) {
                    rowHeights[rr] += per + (rem > 0 ? 1 : 0);
                    if (rem > 0) rem--;
                }
            }
        }

        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            int cs = TableModel.spanCol(host.cell);
            int rs = TableModel.spanRow(host.cell);
            int w = 0;
            for (int cc = aC; cc < aC + cs && cc < colCount; cc++) w += colWidths[cc];
            int h = 0;
            for (int rr = aR; rr < aR + rs && rr < rowCount; rr++) h += rowHeights[rr];
            host.measure(MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY),
                         MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY));
        }

        colStarts = new int[colCount + 1];
        colStarts[0] = padL;
        for (int c = 0; c < colCount; c++) colStarts[c + 1] = colStarts[c] + colWidths[c];
        rowStarts = new int[rowCount + 1];
        rowStarts[0] = padT;
        for (int r = 0; r < rowCount; r++) rowStarts[r + 1] = rowStarts[r] + rowHeights[r];

        int totalW = colStarts[colCount] + padR;
        int totalH = rowStarts[rowCount] + padB;

        setMeasuredDimension(Math.max(totalW, parentWidth + padL + padR), totalH);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        if (model == null) return;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            if (aR < 0 || aC < 0) continue;
            int x = colStarts[aC];
            int y = rowStarts[aR];
            host.layout(x, y, x + host.getMeasuredWidth(), y + host.getMeasuredHeight());
        }
    }

    public int contentRight() {
        if (model == null || model.colCount == 0) return 0;
        return colStarts[model.colCount];
    }

    public int contentBottom() {
        if (model == null || model.rowCount == 0) return 0;
        return rowStarts[model.rowCount];
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        computeHandlesState();
        drawCellBackgrounds(canvas);
        super.dispatchDraw(canvas);
        drawBulgeFills(canvas);
        if (model != null && model.block != null && model.block.bordered) {
            drawBorders(canvas);
        }
        drawSelectionOutline(canvas);
        drawHandleDots(canvas);
    }

    private TL_iv.pageTableCell activeCell() {
        if (model == null || model.rowCount == 0 || model.colCount == 0) return null;
        final TL_iv.pageTableCell f = findFocusedCell();
        if (f != null) return f;
        if (selectionProvider == null) return null;
        TL_iv.pageTableCell best = null;
        int bestR = Integer.MAX_VALUE, bestC = Integer.MAX_VALUE;
        for (TL_iv.pageTableCell c : model.anchors()) {
            if (!selectionProvider.isSelected(c)) continue;
            final int r = model.anchorRowOf(c), col = model.anchorColOf(c);
            if (r < bestR || (r == bestR && col < bestC)) { bestR = r; bestC = col; best = c; }
        }
        return best;
    }

    private boolean isRowFullySelected(int r) {
        if (r < 0 || r >= model.rowCount) return false;
        for (int c = 0; c < model.colCount; c++) if (!isSelected(r, c)) return false;
        return true;
    }

    private boolean rowHasSelection(int r) {
        if (r < 0 || r >= model.rowCount) return false;
        for (int c = 0; c < model.colCount; c++) if (isSelected(r, c)) return true;
        return false;
    }

    private boolean isColFullySelected(int c) {
        if (c < 0 || c >= model.colCount) return false;
        for (int r = 0; r < model.rowCount; r++) if (!isSelected(r, c)) return false;
        return true;
    }

    private boolean colHasSelection(int c) {
        if (c < 0 || c >= model.colCount) return false;
        for (int r = 0; r < model.rowCount; r++) if (isSelected(r, c)) return true;
        return false;
    }

    private int firstSelectedRow() {
        for (int r = 0; r < model.rowCount; r++) if (rowHasSelection(r)) return r;
        return -1;
    }

    private int lastSelectedRow() {
        for (int r = model.rowCount - 1; r >= 0; r--) if (rowHasSelection(r)) return r;
        return -1;
    }

    private int firstSelectedCol() {
        for (int c = 0; c < model.colCount; c++) if (colHasSelection(c)) return c;
        return -1;
    }

    private int lastSelectedCol() {
        for (int c = model.colCount - 1; c >= 0; c--) if (colHasSelection(c)) return c;
        return -1;
    }

    private boolean areRowsFullySelected(int first, int last) {
        if (first < 0 || last < first) return false;
        for (int r = first; r <= last; r++) if (!isRowFullySelected(r)) return false;
        return true;
    }

    private boolean areColsFullySelected(int first, int last) {
        if (first < 0 || last < first) return false;
        for (int c = first; c <= last; c++) if (!isColFullySelected(c)) return false;
        return true;
    }

    private boolean useCombinedRowHandle() {
        if (!hasAnySelection()) return false;
        final boolean fullRows = areRowsFullySelected(firstSelectedRow(), lastSelectedRow());
        final boolean fullCols = areColsFullySelected(firstSelectedCol(), lastSelectedCol());
        return !fullCols || fullRows;
    }

    private boolean useCombinedColHandle() {
        if (!hasAnySelection()) return false;
        final boolean fullRows = areRowsFullySelected(firstSelectedRow(), lastSelectedRow());
        final boolean fullCols = areColsFullySelected(firstSelectedCol(), lastSelectedCol());
        return !fullRows || fullCols;
    }

    private void computeHandlesState() {
        leftBulge = bottomBulge = false;
        final TL_iv.pageTableCell ac = activeCell();
        if (ac == null) return;
        final int aR = model.anchorRowOf(ac), aC = model.anchorColOf(ac);
        if (aR < 0 || aC < 0) return;
        final int rs = TableModel.spanRow(ac), cs = TableModel.spanCol(ac);
        if (isRowFullySelected(aR)) {
            leftBulge = true;
            bulgeRowTop = rowStarts[aR];
            bulgeRowBot = rowStarts[Math.min(aR + rs, model.rowCount)];
        }
        if (isColFullySelected(aC)) {
            bottomBulge = true;
            bulgeColLeft = colStarts[aC];
            bulgeColRight = colStarts[Math.min(aC + cs, model.colCount)];
        }
    }

    private void drawBulgeFills(Canvas canvas) {
        if (hasAnySelection()) {
            final int firstRow = firstSelectedRow(), lastRow = lastSelectedRow();
            final int firstCol = firstSelectedCol(), lastCol = lastSelectedCol();
            if (areRowsFullySelected(firstRow, lastRow)) {
                drawLeftBulgeFill(canvas, rowStarts[firstRow], rowStarts[lastRow + 1]);
            }
            if (areColsFullySelected(firstCol, lastCol)) {
                drawBottomBulgeFill(canvas, colStarts[firstCol], colStarts[lastCol + 1]);
            }
            return;
        }
        if (leftBulge) {
            drawLeftBulgeFill(canvas, bulgeRowTop, bulgeRowBot);
        }
        if (bottomBulge) {
            drawBottomBulgeFill(canvas, bulgeColLeft, bulgeColRight);
        }
    }

    private void drawLeftBulgeFill(Canvas canvas, int rowTop, int rowBottom) {
        final float top = rowTop - dpf2(1), bot = rowBottom + dpf2(1);
        final float outer = colStarts[0] - dp(BULGE_DP);
        final float bR = Math.min(dpf2(BULGE_RADIUS_DP), (bot - top) / 2f);
        final float tRtop = cornerRadiusFor(colStarts[0], rowTop);
        final float tRbot = cornerRadiusFor(colStarts[0], rowBottom);
        bulgePath.rewind();
        bulgePath.moveTo(colStarts[0] + tRtop, top);
        bulgePath.lineTo(outer + bR, top);

        arcRect.set(outer, top, outer + 2 * bR, top + 2 * bR);
        bulgePath.arcTo(arcRect, 270, -90);
        bulgePath.lineTo(outer, bot - bR);

        arcRect.set(outer, bot - 2 * bR, outer + 2 * bR, bot);
        bulgePath.arcTo(arcRect, 180, -90);
        bulgePath.lineTo(colStarts[0] + tRbot, bot);

        if (tRbot > 0) {
            arcRect.set(colStarts[0], bot - 2 * tRbot, colStarts[0] + 2 * tRbot, bot);
            bulgePath.arcTo(arcRect, 90, 90);
        } else {
            bulgePath.lineTo(colStarts[0], bot);
        }

        bulgePath.lineTo(colStarts[0], top + tRtop);
        if (tRtop > 0) {
            arcRect.set(colStarts[0], top, colStarts[0] + 2 * tRtop, top + 2 * tRtop);
            bulgePath.arcTo(arcRect, 180, 90);
        } else {
            bulgePath.lineTo(colStarts[0], top);
        }

        bulgePath.close();
        canvas.drawPath(bulgePath, bulgeFillPaint);
    }

    private void drawBottomBulgeFill(Canvas canvas, int colLeft, int colRight) {
        final float left = colLeft - dpf2(1), right = colRight + dpf2(1);
        final float tableY = rowStarts[model.rowCount];
        final float outer = tableY + dp(BULGE_DP);
        final float bR = Math.min(dpf2(BULGE_RADIUS_DP), (right - left) / 2f);
        final float tRleft = cornerRadiusFor(colLeft, rowStarts[model.rowCount]);
        final float tRright = cornerRadiusFor(colRight, rowStarts[model.rowCount]);
        bulgePath.rewind();
        bulgePath.moveTo(left, tableY - tRleft);
        bulgePath.lineTo(left, outer - bR);
        arcRect.set(left, outer - 2 * bR, left + 2 * bR, outer);
        bulgePath.arcTo(arcRect, 180, -90);
        bulgePath.lineTo(right - bR, outer);
        arcRect.set(right - 2 * bR, outer - 2 * bR, right, outer);
        bulgePath.arcTo(arcRect, 90, -90);
        bulgePath.lineTo(right, tableY - tRright);
        if (tRright > 0) {
            // Curve up into the table corner so the fill follows the table's rounded corner.
            arcRect.set(right - 2 * tRright, tableY - 2 * tRright, right, tableY);
            bulgePath.arcTo(arcRect, 0, 90);
        } else {
            bulgePath.lineTo(right, tableY);
        }
        bulgePath.lineTo(left + tRleft, tableY);
        if (tRleft > 0) {
            arcRect.set(left, tableY - 2 * tRleft, left + 2 * tRleft, tableY);
            bulgePath.arcTo(arcRect, 90, 90);
        } else {
            bulgePath.lineTo(left, tableY);
        }
        bulgePath.close();
        canvas.drawPath(bulgePath, bulgeFillPaint);
    }

    private void drawBulgeOutlines(Canvas canvas) {
        if (leftBulge) {
            final float outer = colStarts[0] - dp(BULGE_DP);
            final float rr = Math.min(dpf2(BULGE_RADIUS_DP), (bulgeRowBot - bulgeRowTop) / 2f);
            bulgePath.rewind();
//            bulgePath.moveTo(colStarts[0], bulgeRowTop);
//            bulgePath.lineTo(outer + rr, bulgeRowTop);

//            arcRect.set(outer, bulgeRowTop, outer + 2 * rr, bulgeRowTop + 2 * rr);
//            bulgePath.arcTo(arcRect, 270, -90);
//            bulgePath.lineTo(outer, bulgeRowBot - rr);
//
//            arcRect.set(outer, bulgeRowBot - 2 * rr, outer + 2 * rr, bulgeRowBot);
//            bulgePath.arcTo(arcRect, 180, -90);
//            bulgePath.lineTo(colStarts[0], bulgeRowBot);

            canvas.drawPath(bulgePath, selectedStrokePaint);
        }
        if (bottomBulge) {
            final float bottom = rowStarts[model.rowCount];
            final float outer = bottom + dp(BULGE_DP);
            final float rr = Math.min(dpf2(BULGE_RADIUS_DP), (bulgeColRight - bulgeColLeft) / 2f);
            bulgePath.rewind();
            bulgePath.moveTo(bulgeColLeft, bottom);
            bulgePath.lineTo(bulgeColLeft, outer - rr);

            arcRect.set(bulgeColLeft, outer - 2 * rr, bulgeColLeft + 2 * rr, outer);
            bulgePath.arcTo(arcRect, 180, -90);
            bulgePath.lineTo(bulgeColRight - rr, outer);

            arcRect.set(bulgeColRight - 2 * rr, outer - 2 * rr, bulgeColRight, outer);
            bulgePath.arcTo(arcRect, 90, -90);
            bulgePath.lineTo(bulgeColRight, bottom);

            canvas.drawPath(bulgePath, selectedStrokePaint);
        }
    }

    private void drawHandleDots(Canvas canvas) {
        if (model == null) return;
        final float radius = dpf2(3) / 2f;
        final float step = dp(8);
        final float lx = colStarts[0] - dp(DOT_AWAY_DP) - radius;
        final float by = rowStarts[model.rowCount] + dp(DOT_AWAY_DP) + radius;
        if (hasAnySelection()) {
            final int firstRow = firstSelectedRow(), lastRow = lastSelectedRow();
            final int firstCol = firstSelectedCol(), lastCol = lastSelectedCol();
            if (firstRow < 0 || firstCol < 0) return;
            final TL_iv.pageTableCell ac = activeCell();
            final boolean combinedRow = useCombinedRowHandle();
            final int activeRow = ac == null ? firstRow : model.anchorRowOf(ac);
            final int activeRowEnd = ac == null ? activeRow + 1
                : Math.min(activeRow + TableModel.spanRow(ac), model.rowCount);
            final float cy = combinedRow
                ? (rowStarts[firstRow] + rowStarts[lastRow + 1]) / 2f
                : (rowStarts[activeRow] + rowStarts[activeRowEnd]) / 2f;
            dotPaint.setColor(combinedRow && areRowsFullySelected(firstRow, lastRow) ? dotOnSelectionColor : dotColor);
            for (int i = -1; i <= 1; i++) canvas.drawCircle(lx, cy + i * step, radius, dotPaint);
            final boolean combinedCol = useCombinedColHandle();
            final int activeCol = ac == null ? firstCol : model.anchorColOf(ac);
            final int activeColEnd = ac == null ? activeCol + 1
                : Math.min(activeCol + TableModel.spanCol(ac), model.colCount);
            final float cx = combinedCol
                ? (colStarts[firstCol] + colStarts[lastCol + 1]) / 2f
                : (colStarts[activeCol] + colStarts[activeColEnd]) / 2f;
            dotPaint.setColor(combinedCol && areColsFullySelected(firstCol, lastCol) ? dotOnSelectionColor : dotColor);
            for (int i = -1; i <= 1; i++) canvas.drawCircle(cx + i * step, by, radius, dotPaint);
            return;
        }
        final TL_iv.pageTableCell ac = activeCell();
        if (ac == null) return;
        final int aR = model.anchorRowOf(ac), aC = model.anchorColOf(ac);
        if (aR < 0 || aC < 0) return;
        final int rs = TableModel.spanRow(ac), cs = TableModel.spanCol(ac);
        final float cy = (rowStarts[aR] + rowStarts[Math.min(aR + rs, model.rowCount)]) / 2f;
        dotPaint.setColor(leftBulge ? dotOnSelectionColor : dotColor);
        for (int i = -1; i <= 1; i++) canvas.drawCircle(lx, cy + i * step, radius, dotPaint);
        final float cx = (colStarts[aC] + colStarts[Math.min(aC + cs, model.colCount)]) / 2f;
        dotPaint.setColor(bottomBulge ? dotOnSelectionColor : dotColor);
        for (int i = -1; i <= 1; i++) canvas.drawCircle(cx + i * step, by, radius, dotPaint);
    }

    public int rowHandleAtGrid(int gx, int gy) {
        if (model == null || gx < colStarts[0] - dp(BULGE_DP) - dp(4) || gx >= colStarts[0]) return -1;
        if (useCombinedRowHandle()) {
            final int first = firstSelectedRow(), last = lastSelectedRow();
            return first >= 0 && gy >= rowStarts[first] && gy < rowStarts[last + 1] ? first : -1;
        }
        final TL_iv.pageTableCell ac = activeCell();
        if (ac == null) return -1;
        final int aR = model.anchorRowOf(ac);
        if (aR < 0) return -1;
        final int rs = TableModel.spanRow(ac);
        final int top = rowStarts[aR], bot = rowStarts[Math.min(aR + rs, model.rowCount)];
        if (gy >= top && gy < bot) return aR;
        return -1;
    }

    public int rowHandleEnd(int start) {
        if (!useCombinedRowHandle() || start != firstSelectedRow()) return start;
        return lastSelectedRow();
    }

    public int colHandleAtGrid(int gx, int gy) {
        if (model == null) return -1;
        final int bottom = rowStarts[model.rowCount];
        if (gy < bottom || gy >= bottom + dp(BULGE_DP) + dp(4)) return -1;
        if (useCombinedColHandle()) {
            final int first = firstSelectedCol(), last = lastSelectedCol();
            return first >= 0 && gx >= colStarts[first] && gx < colStarts[last + 1] ? first : -1;
        }
        final TL_iv.pageTableCell ac = activeCell();
        if (ac == null) return -1;
        final int aC = model.anchorColOf(ac);
        if (aC < 0) return -1;
        final int cs = TableModel.spanCol(ac);
        final int left = colStarts[aC], right = colStarts[Math.min(aC + cs, model.colCount)];
        if (gx >= left && gx < right) return aC;
        return -1;
    }

    public int colHandleEnd(int start) {
        if (!useCombinedColHandle() || start != firstSelectedCol()) return start;
        return lastSelectedCol();
    }

    public TL_iv.pageTableCell findFocusedCell() {
        final View f = findFocus();
        ViewParent p = f == null ? null : f.getParent();
        while (p != null && p != this) {
            if (p instanceof RichTableCellHost) return ((RichTableCellHost) p).cell;
            p = p.getParent();
        }
        return null;
    }

    private void drawCellBackgrounds(Canvas canvas) {
        if (model == null || model.rowCount == 0 || model.colCount == 0) return;
        boolean striped = model.block != null && model.block.striped;
        // Clip cell backgrounds to the table's rounded outline so highlight fills don't spill past
        // the rounded corners.
        canvas.save();
        selRect.set(colStarts[0], rowStarts[0], colStarts[model.colCount], rowStarts[model.rowCount]);
        bulgePath.rewind();
        bulgePath.addRoundRect(selRect, dpf2(10), dpf2(10), android.graphics.Path.Direction.CW);
        canvas.clipPath(bulgePath);
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (!(child instanceof RichTableCellHost)) continue;
            RichTableCellHost host = (RichTableCellHost) child;
            int aR = model.anchorRowOf(host.cell);
            int aC = model.anchorColOf(host.cell);
            if (aR < 0 || aC < 0) continue;
            int cs = TableModel.spanCol(host.cell);
            int rs = TableModel.spanRow(host.cell);
            int x = colStarts[aC];
            int y = rowStarts[aR];
            int x2 = colStarts[Math.min(aC + cs, model.colCount)];
            int y2 = rowStarts[Math.min(aR + rs, model.rowCount)];
            if (host.cell.header) {
                canvas.drawRect(x, y, x2, y2, headerPaint);
            } else if (striped && aR % 2 == 0) {
                canvas.drawRect(x, y, x2, y2, stripPaint);
            }
        }
        canvas.restore();
    }

    private boolean isSelected(int r, int c) {
        if (model == null || selectionProvider == null) return false;
        if (r < 0 || r >= model.rowCount || c < 0 || c >= model.colCount) return false;
        return selectionProvider.isSelected(model.grid[r][c]);
    }

    private boolean hasAnySelection() {
        if (model == null || selectionProvider == null) return false;
        for (TL_iv.pageTableCell c : model.anchors()) {
            if (selectionProvider.isSelected(c)) return true;
        }
        return false;
    }

    private void drawSelectionOutline(Canvas canvas) {
        if (model == null) return;
        float v = selectionFade.set(hasAnySelection() ? 1f : 0f);
        if (v <= 0.001f) return;
        selectedStrokePaint.setAlpha((int) (selectedStrokeBaseAlpha * v));
        selectedStrokePaint.setStrokeWidth(dpf2(2) * Math.max(0.4f, v));

        for (int r = 0; r <= model.rowCount; r++) {
            int y = r < model.rowCount ? rowStarts[r] : rowStarts[model.rowCount];
            int xStart = -1;
            for (int c = 0; c < model.colCount; c++) {
                boolean above = isSelected(r - 1, c);
                boolean below = isSelected(r, c);
                if (above != below) {
                    if (xStart < 0) xStart = colStarts[c];
                } else if (xStart >= 0) {
                    drawSelHLine(canvas, xStart, colStarts[c], y);
                    xStart = -1;
                }
            }
            if (xStart >= 0) {
                drawSelHLine(canvas, xStart, colStarts[model.colCount], y);
            }
        }
        for (int c = 0; c <= model.colCount; c++) {
            int x = c < model.colCount ? colStarts[c] : colStarts[model.colCount];
            int yStart = -1;
            for (int r = 0; r < model.rowCount; r++) {
                boolean leftSel = isSelected(r, c - 1);
                boolean rightSel = isSelected(r, c);
                if (leftSel != rightSel) {
                    if (yStart < 0) yStart = rowStarts[r];
                } else if (yStart >= 0) {
                    drawSelVLine(canvas, x, yStart, rowStarts[r]);
                    yStart = -1;
                }
            }
            if (yStart >= 0) {
                drawSelVLine(canvas, x, yStart, rowStarts[model.rowCount]);
            }
        }

        final int colN = model.colCount, rowN = model.rowCount;
        drawSelCornerArc(canvas, colStarts[0], rowStarts[0], 180);     // top-left
        drawSelCornerArc(canvas, colStarts[colN], rowStarts[0], 270);  // top-right
        drawSelCornerArc(canvas, colStarts[0], rowStarts[rowN], 90);   // bottom-left
        drawSelCornerArc(canvas, colStarts[colN], rowStarts[rowN], 0); // bottom-right
    }

    private float cornerRadiusFor(int px, int py) {
        final int colN = model.colCount, rowN = model.rowCount;
        final int cc;
        if (px == colStarts[0]) cc = 0;
        else if (px == colStarts[colN]) cc = colN - 1;
        else return 0;
        final int cr;
        if (py == rowStarts[0]) cr = 0;
        else if (py == rowStarts[rowN]) cr = rowN - 1;
        else return 0;
        if (cc < 0 || cr < 0 || !isSelected(cr, cc)) return 0;
        return Math.min(dpf2(10), Math.min(colWidths[cc], rowHeights[cr]) / 2f);
    }

    private void drawSelHLine(Canvas canvas, int xStart, int xEnd, int y) {
        if (xEnd <= xStart) return;
        final float a = xStart + cornerRadiusFor(xStart, y);
        final float b = xEnd - cornerRadiusFor(xEnd, y);
        if (b > a) canvas.drawLine(a, y, b, y, selectedStrokePaint);
    }

    private void drawSelVLine(Canvas canvas, int x, int yStart, int yEnd) {
        if (yEnd <= yStart) return;
        final float a = yStart + cornerRadiusFor(x, yStart);
        final float b = yEnd - cornerRadiusFor(x, yEnd);
        if (b > a) canvas.drawLine(x, a, x, b, selectedStrokePaint);
    }

    private void drawSelCornerArc(Canvas canvas, int px, int py, float startAngle) {
        final float rr = cornerRadiusFor(px, py);
        if (rr <= 0) return;
        final float cx = px == colStarts[0] ? px + rr : px - rr;
        final float cy = py == rowStarts[0] ? py + rr : py - rr;
        arcRect.set(cx - rr, cy - rr, cx + rr, cy + rr);
        canvas.drawArc(arcRect, startAngle, 90, false, selectedStrokePaint);
    }

    private void drawBorders(Canvas canvas) {
        float halfStroke = linePaint.getStrokeWidth() / 2f;
        float r = dpf2(10);

        selRect.set(colStarts[0] + halfStroke, rowStarts[0] + halfStroke,
            colStarts[model.colCount] - halfStroke, rowStarts[model.rowCount] - halfStroke);
        canvas.drawRoundRect(selRect, r, r, linePaint);

        for (int c = 1; c < model.colCount; c++) {
            int x = colStarts[c];
            int yStart = -1;
            for (int rr = 0; rr < model.rowCount; rr++) {
                boolean draw = model.grid[rr][c - 1] != model.grid[rr][c];
                if (draw) {
                    if (yStart < 0) yStart = rowStarts[rr];
                } else if (yStart >= 0) {
                    canvas.drawLine(x, yStart, x, rowStarts[rr], linePaint);
                    yStart = -1;
                }
            }
            if (yStart >= 0) {
                canvas.drawLine(x, yStart, x, rowStarts[model.rowCount], linePaint);
            }
        }
        for (int rr = 1; rr < model.rowCount; rr++) {
            int y = rowStarts[rr];
            int xStart = -1;
            for (int c = 0; c < model.colCount; c++) {
                boolean draw = model.grid[rr - 1][c] != model.grid[rr][c];
                if (draw) {
                    if (xStart < 0) xStart = colStarts[c];
                } else if (xStart >= 0) {
                    canvas.drawLine(xStart, y, colStarts[c], y, linePaint);
                    xStart = -1;
                }
            }
            if (xStart >= 0) {
                canvas.drawLine(xStart, y, colStarts[model.colCount], y, linePaint);
            }
        }
    }

    public int columnStart(int c) {
        if (c < 0 || c >= colStarts.length) return 0;
        return colStarts[c];
    }

    public int rowStart(int r) {
        if (r < 0 || r >= rowStarts.length) return 0;
        return rowStarts[r];
    }
}
