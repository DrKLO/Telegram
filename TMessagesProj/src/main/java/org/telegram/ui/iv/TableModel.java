package org.telegram.ui.iv;

import org.telegram.tgnet.TLObject;
import org.telegram.tgnet.tl.TL_iv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class TableModel {

    public final TL_iv.pageBlockTable block;
    public int rowCount;
    public int colCount;
    public TL_iv.pageTableCell[][] grid;
    public int[][] anchorR;
    public int[][] anchorC;
    private final ArrayList<TL_iv.pageTableCell> anchorsRowMajor = new ArrayList<>();

    public TableModel(TL_iv.pageBlockTable block) {
        this.block = block;
        rebuildFromBlock();
    }

    public void rebuildFromBlock() {
        rowCount = block.rows == null ? 0 : block.rows.size();

        int estCols = 0;
        for (int r = 0; r < rowCount; r++) {
            TL_iv.pageTableRow row = block.rows.get(r);
            int sum = 0;
            for (int i = 0; i < row.cells.size(); i++) {
                sum += spanCol(row.cells.get(i));
            }
            if (sum > estCols) estCols = sum;
        }

        TL_iv.pageTableCell[][] tmpGrid = new TL_iv.pageTableCell[Math.max(rowCount, 1)][Math.max(estCols, 1)];
        int[][] tmpAR = new int[Math.max(rowCount, 1)][Math.max(estCols, 1)];
        int[][] tmpAC = new int[Math.max(rowCount, 1)][Math.max(estCols, 1)];
        for (int r = 0; r < tmpAR.length; r++) {
            for (int c = 0; c < tmpAR[0].length; c++) {
                tmpAR[r][c] = -1;
                tmpAC[r][c] = -1;
            }
        }

        int filledCols = 0;
        for (int r = 0; r < rowCount; r++) {
            TL_iv.pageTableRow row = block.rows.get(r);
            int c = 0;
            for (int i = 0; i < row.cells.size(); i++) {
                TL_iv.pageTableCell cell = row.cells.get(i);
                int cs = spanCol(cell);
                int rs = spanRow(cell);

                while (c < estCols && tmpGrid[r][c] != null) c++;
                if (c + cs > estCols) {
                    int newCols = Math.max(c + cs, estCols * 2);
                    tmpGrid = growCols(tmpGrid, newCols);
                    tmpAR = growIntCols(tmpAR, newCols, -1);
                    tmpAC = growIntCols(tmpAC, newCols, -1);
                    estCols = newCols;
                }

                for (int rr = r; rr < r + rs && rr < rowCount; rr++) {
                    for (int cc = c; cc < c + cs; cc++) {
                        tmpGrid[rr][cc] = cell;
                        tmpAR[rr][cc] = r;
                        tmpAC[rr][cc] = c;
                    }
                }
                if (c + cs > filledCols) filledCols = c + cs;
                c += cs;
            }
        }

        colCount = filledCols;

        grid = new TL_iv.pageTableCell[Math.max(rowCount, 1)][Math.max(colCount, 1)];
        anchorR = new int[Math.max(rowCount, 1)][Math.max(colCount, 1)];
        anchorC = new int[Math.max(rowCount, 1)][Math.max(colCount, 1)];
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                grid[r][c] = tmpGrid[r][c];
                anchorR[r][c] = tmpAR[r][c];
                anchorC[r][c] = tmpAC[r][c];
                if (grid[r][c] == null) {
                    TL_iv.pageTableCell empty = newEmptyCell();
                    grid[r][c] = empty;
                    anchorR[r][c] = r;
                    anchorC[r][c] = c;
                    block.rows.get(r).cells.add(empty);
                }
            }
        }

        rebuildAnchorList();
    }

    public boolean isAnchor(int r, int c) {
        return r >= 0 && c >= 0 && r < rowCount && c < colCount
            && anchorR[r][c] == r && anchorC[r][c] == c;
    }

    public List<TL_iv.pageTableCell> anchors() {
        return anchorsRowMajor;
    }

    public int flatIndexOfAnchor(TL_iv.pageTableCell cell) {
        return anchorsRowMajor.indexOf(cell);
    }

    public TL_iv.pageTableCell anchorAt(int flatIndex) {
        if (flatIndex < 0 || flatIndex >= anchorsRowMajor.size()) return null;
        return anchorsRowMajor.get(flatIndex);
    }

    public int anchorRowOf(TL_iv.pageTableCell cell) {
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                if (grid[r][c] == cell) return anchorR[r][c];
            }
        }
        return -1;
    }

    public int anchorColOf(TL_iv.pageTableCell cell) {
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                if (grid[r][c] == cell) return anchorC[r][c];
            }
        }
        return -1;
    }

    public static int spanCol(TL_iv.pageTableCell cell) {
        return cell.colspan != 0 ? cell.colspan : 1;
    }

    public static int spanRow(TL_iv.pageTableCell cell) {
        return cell.rowspan != 0 ? cell.rowspan : 1;
    }

    public static TL_iv.pageTableCell newEmptyCell() {
        TL_iv.pageTableCell cell = new TL_iv.pageTableCell();
        applyPlainText(cell, "");
        return cell;
    }

    public static void setHeader(TL_iv.pageTableCell cell, boolean header) {
        if (cell == null) return;
        cell.header = header;
        if (header) cell.flags |= TLObject.FLAG_0; else cell.flags &= ~TLObject.FLAG_0;
    }

    // Horizontal alignment: 0 = left, 1 = center, 2 = right.
    public static final int ALIGN_LEFT = 0, ALIGN_CENTER = 1, ALIGN_RIGHT = 2;
    // Vertical alignment: 0 = top, 1 = middle, 2 = bottom.
    public static final int VALIGN_TOP = 0, VALIGN_MIDDLE = 1, VALIGN_BOTTOM = 2;

    public static int alignOf(TL_iv.pageTableCell cell) {
        if (cell == null) return ALIGN_LEFT;
        if (cell.align_right) return ALIGN_RIGHT;
        if (cell.align_center) return ALIGN_CENTER;
        return ALIGN_LEFT;
    }

    public static int valignOf(TL_iv.pageTableCell cell) {
        if (cell == null) return VALIGN_TOP;
        if (cell.valign_bottom) return VALIGN_BOTTOM;
        if (cell.valign_middle) return VALIGN_MIDDLE;
        return VALIGN_TOP;
    }

    public static void setAlign(TL_iv.pageTableCell cell, int align) {
        if (cell == null) return;
        cell.align_center = align == ALIGN_CENTER;
        cell.align_right = align == ALIGN_RIGHT;
        if (cell.align_center) cell.flags |= TLObject.FLAG_3; else cell.flags &= ~TLObject.FLAG_3;
        if (cell.align_right) cell.flags |= TLObject.FLAG_4; else cell.flags &= ~TLObject.FLAG_4;
    }

    public static void setVAlign(TL_iv.pageTableCell cell, int valign) {
        if (cell == null) return;
        cell.valign_middle = valign == VALIGN_MIDDLE;
        cell.valign_bottom = valign == VALIGN_BOTTOM;
        if (cell.valign_middle) cell.flags |= TLObject.FLAG_5; else cell.flags &= ~TLObject.FLAG_5;
        if (cell.valign_bottom) cell.flags |= TLObject.FLAG_6; else cell.flags &= ~TLObject.FLAG_6;
    }

    public void addRow() {
        TL_iv.pageTableRow row = new TL_iv.pageTableRow();
        row.cells = new ArrayList<>();
        int cols = Math.max(colCount, 1);
        for (int c = 0; c < cols; c++) row.cells.add(newEmptyCell());
        block.rows.add(row);
        rebuildFromBlock();
    }

    public void addColumn() {
        if (block.rows.isEmpty()) {
            TL_iv.pageTableRow row = new TL_iv.pageTableRow();
            row.cells = new ArrayList<>();
            row.cells.add(newEmptyCell());
            block.rows.add(row);
        } else {
            for (TL_iv.pageTableRow row : block.rows) {
                if (row.cells == null) row.cells = new ArrayList<>();
                row.cells.add(newEmptyCell());
            }
        }
        rebuildFromBlock();
    }

    public boolean insertRowAt(int idx) {
        if (rowCount == 0 || colCount == 0) { addRow(); return true; }
        if (idx < 0) idx = 0;
        if (idx > rowCount) idx = rowCount;
        final int insertAt = idx;
        IdentityHashMap<TL_iv.pageTableCell, int[]> meta = new IdentityHashMap<>();
        boolean[] coveredCols = new boolean[colCount];
        for (TL_iv.pageTableCell c : anchorsRowMajor) {
            int oldR = anchorRowOf(c), oldC = anchorColOf(c);
            int rs = spanRow(c), cs = spanCol(c);
            int newR = oldR >= insertAt ? oldR + 1 : oldR;
            int newRs = rs;
            if (oldR < insertAt && oldR + rs > insertAt) {
                newRs = rs + 1;
                for (int cc = oldC; cc < oldC + cs && cc < colCount; cc++) coveredCols[cc] = true;
            }
            meta.put(c, new int[] { newR, oldC, newRs, cs });
        }
        for (int c = 0; c < colCount; c++) {
            if (coveredCols[c]) continue;
            meta.put(newEmptyCell(), new int[] { insertAt, c, 1, 1 });
        }
        rewriteBlockRows(meta, rowCount + 1);
        rebuildFromBlock();
        return true;
    }

    public boolean insertColumnAt(int idx) {
        if (rowCount == 0 || colCount == 0) { addColumn(); return true; }
        if (idx < 0) idx = 0;
        if (idx > colCount) idx = colCount;
        final int insertAt = idx;
        IdentityHashMap<TL_iv.pageTableCell, int[]> meta = new IdentityHashMap<>();
        boolean[] coveredRows = new boolean[rowCount];
        for (TL_iv.pageTableCell c : anchorsRowMajor) {
            int oldR = anchorRowOf(c), oldC = anchorColOf(c);
            int rs = spanRow(c), cs = spanCol(c);
            int newC = oldC >= insertAt ? oldC + 1 : oldC;
            int newCs = cs;
            if (oldC < insertAt && oldC + cs > insertAt) {
                newCs = cs + 1;
                for (int rr = oldR; rr < oldR + rs && rr < rowCount; rr++) coveredRows[rr] = true;
            }
            meta.put(c, new int[] { oldR, newC, rs, newCs });
        }
        for (int r = 0; r < rowCount; r++) {
            if (coveredRows[r]) continue;
            meta.put(newEmptyCell(), new int[] { r, insertAt, 1, 1 });
        }
        rewriteBlockRows(meta, rowCount);
        rebuildFromBlock();
        return true;
    }

    public boolean mergeCells(Set<TL_iv.pageTableCell> sel) {
        if (sel == null || sel.size() < 2) return false;
        int minR = Integer.MAX_VALUE, minC = Integer.MAX_VALUE, maxR = -1, maxC = -1;
        for (TL_iv.pageTableCell c : sel) {
            int ar = anchorRowOf(c), ac = anchorColOf(c);
            int rs = spanRow(c), cs = spanCol(c);
            minR = Math.min(minR, ar); minC = Math.min(minC, ac);
            maxR = Math.max(maxR, ar + rs - 1); maxC = Math.max(maxC, ac + cs - 1);
        }
        HashSet<TL_iv.pageTableCell> coveredAnchors = new HashSet<>();
        for (int r = minR; r <= maxR; r++) {
            for (int c = minC; c <= maxC; c++) {
                if (r < 0 || c < 0 || r >= rowCount || c >= colCount) return false;
                coveredAnchors.add(grid[r][c]);
            }
        }
        if (!coveredAnchors.equals(new HashSet<>(sel))) return false;

        StringBuilder mergedText = new StringBuilder();
        ArrayList<TL_iv.pageTableCell> ordered = new ArrayList<>(coveredAnchors);
        Collections.sort(ordered, (a, b) -> {
            int ra = anchorRowOf(a), rb = anchorRowOf(b);
            if (ra != rb) return Integer.compare(ra, rb);
            return Integer.compare(anchorColOf(a), anchorColOf(b));
        });
        for (TL_iv.pageTableCell c : ordered) {
            String t = readPlainText(c);
            if (!t.isEmpty()) {
                if (mergedText.length() > 0) mergedText.append("\n");
                mergedText.append(t);
            }
        }

        TL_iv.pageTableCell topLeft = grid[minR][minC];
        int newCs = maxC - minC + 1;
        int newRs = maxR - minR + 1;
        topLeft.colspan = newCs > 1 ? newCs : 0;
        topLeft.rowspan = newRs > 1 ? newRs : 0;
        if (topLeft.colspan > 0) topLeft.flags |= TLObject.FLAG_1; else topLeft.flags &= ~TLObject.FLAG_1;
        if (topLeft.rowspan > 0) topLeft.flags |= TLObject.FLAG_2; else topLeft.flags &= ~TLObject.FLAG_2;
        applyPlainText(topLeft, mergedText.toString());

        for (TL_iv.pageTableCell c : coveredAnchors) {
            if (c == topLeft) continue;
            int ar = anchorRowOf(c);
            if (ar >= 0 && ar < block.rows.size()) {
                block.rows.get(ar).cells.remove(c);
            }
        }
        rebuildFromBlock();
        return true;
    }

    public boolean unmergeCell(TL_iv.pageTableCell anchorCell) {
        if (anchorCell == null) return false;
        int anchorR = anchorRowOf(anchorCell);
        int anchorC = anchorColOf(anchorCell);
        if (anchorR < 0 || anchorC < 0) return false;
        int rs = spanRow(anchorCell);
        int cs = spanCol(anchorCell);
        if (rs <= 1 && cs <= 1) return false;

        anchorCell.rowspan = 0;
        anchorCell.colspan = 0;
        anchorCell.flags &= ~TLObject.FLAG_1;
        anchorCell.flags &= ~TLObject.FLAG_2;

        for (int rr = anchorR; rr < anchorR + rs && rr < rowCount; rr++) {
            TL_iv.pageTableRow row = block.rows.get(rr);
            ArrayList<Object[]> items = new ArrayList<>();
            for (TL_iv.pageTableCell ec : row.cells) {
                items.add(new Object[] { ec, anchorColOf(ec) });
            }
            for (int cc = anchorC; cc < anchorC + cs; cc++) {
                if (rr == anchorR && cc == anchorC) continue;
                TL_iv.pageTableCell nc = new TL_iv.pageTableCell();
                nc.header = anchorCell.header;
                nc.align_center = anchorCell.align_center;
                nc.align_right = anchorCell.align_right;
                nc.valign_middle = anchorCell.valign_middle;
                nc.valign_bottom = anchorCell.valign_bottom;
                applyPlainText(nc, "");
                items.add(new Object[] { nc, cc });
            }
            Collections.sort(items, Comparator.comparingInt(a -> (int) a[1]));
            row.cells.clear();
            for (Object[] item : items) row.cells.add((TL_iv.pageTableCell) item[0]);
        }
        rebuildFromBlock();
        return true;
    }

    public boolean deleteRows(java.util.Set<Integer> rowsToDelete) {
        if (rowsToDelete == null || rowsToDelete.isEmpty()) return false;
        boolean[] deleted = new boolean[rowCount];
        for (int r : rowsToDelete) if (r >= 0 && r < rowCount) deleted[r] = true;
        int newCount = 0;
        int[] newRowIndex = new int[rowCount];
        for (int r = 0; r < rowCount; r++) {
            newRowIndex[r] = newCount;
            if (!deleted[r]) newCount++;
        }
        if (newCount == 0) {
            block.rows.clear();
            rebuildFromBlock();
            return true;
        }
        java.util.IdentityHashMap<TL_iv.pageTableCell, int[]> meta = new java.util.IdentityHashMap<>();
        for (TL_iv.pageTableCell c : anchorsRowMajor) {
            int oldR = anchorRowOf(c);
            int oldC = anchorColOf(c);
            int rs = spanRow(c);
            int cs = spanCol(c);
            int firstKept = -1;
            int keptCount = 0;
            for (int r = oldR; r < oldR + rs && r < rowCount; r++) {
                if (!deleted[r]) {
                    if (firstKept < 0) firstKept = r;
                    keptCount++;
                }
            }
            if (firstKept < 0) continue;
            meta.put(c, new int[] { newRowIndex[firstKept], oldC, keptCount, cs });
        }
        rewriteBlockRows(meta, newCount);
        rebuildFromBlock();
        return true;
    }

    public boolean deleteColumns(java.util.Set<Integer> colsToDelete) {
        if (colsToDelete == null || colsToDelete.isEmpty()) return false;
        boolean[] deleted = new boolean[colCount];
        for (int c : colsToDelete) if (c >= 0 && c < colCount) deleted[c] = true;
        int newCount = 0;
        int[] newColIndex = new int[colCount];
        for (int c = 0; c < colCount; c++) {
            newColIndex[c] = newCount;
            if (!deleted[c]) newCount++;
        }
        if (newCount == 0) {
            block.rows.clear();
            rebuildFromBlock();
            return true;
        }
        java.util.IdentityHashMap<TL_iv.pageTableCell, int[]> meta = new java.util.IdentityHashMap<>();
        for (TL_iv.pageTableCell c : anchorsRowMajor) {
            int oldR = anchorRowOf(c);
            int oldC = anchorColOf(c);
            int rs = spanRow(c);
            int cs = spanCol(c);
            int firstKept = -1;
            int keptCount = 0;
            for (int col = oldC; col < oldC + cs && col < colCount; col++) {
                if (!deleted[col]) {
                    if (firstKept < 0) firstKept = col;
                    keptCount++;
                }
            }
            if (firstKept < 0) continue;
            meta.put(c, new int[] { oldR, newColIndex[firstKept], rs, keptCount });
        }
        rewriteBlockRows(meta, rowCount);
        rebuildFromBlock();
        return true;
    }

    private void rewriteBlockRows(IdentityHashMap<TL_iv.pageTableCell, int[]> meta, int newRowCount) {
        block.rows.clear();
        for (int newR = 0; newR < newRowCount; newR++) {
            final TL_iv.pageTableRow row = new TL_iv.pageTableRow();
            row.cells = new ArrayList<>();
            ArrayList<TL_iv.pageTableCell> rowCells = new ArrayList<>();
            for (Map.Entry<TL_iv.pageTableCell, int[]> e : meta.entrySet()) {
                if (e.getValue()[0] == newR) rowCells.add(e.getKey());
            }
            final IdentityHashMap<TL_iv.pageTableCell, int[]> metaFinal = meta;
            Collections.sort(rowCells, Comparator.comparingInt(a -> metaFinal.get(a)[1]));
            for (TL_iv.pageTableCell c : rowCells) {
                int[] m = meta.get(c);
                c.rowspan = m[2] > 1 ? m[2] : 0;
                c.colspan = m[3] > 1 ? m[3] : 0;
                if (c.rowspan != 0) c.flags |= TLObject.FLAG_2; else c.flags &= ~TLObject.FLAG_2;
                if (c.colspan != 0) c.flags |= TLObject.FLAG_1; else c.flags &= ~TLObject.FLAG_1;
                row.cells.add(c);
            }
            block.rows.add(row);
        }
    }

    public static void normalizeForSend(TL_iv.pageBlockTable t) {
        if (t == null) return;
        if (t.title == null) t.title = new TL_iv.textEmpty();
        if (t.rows == null) return;
        for (int r = 0; r < t.rows.size(); r++) {
            final TL_iv.pageTableRow row = t.rows.get(r);
            if (row.cells == null) continue;
            for (int c = 0; c < row.cells.size(); c++) {
                TL_iv.pageTableCell cell = row.cells.get(c);
                if (cell.text == null) {
                    applyPlainText(cell, "");
                } else {
                    cell.flags |= TLObject.FLAG_7;
                }
                if (cell.colspan > 1) cell.flags |= TLObject.FLAG_1; else cell.flags &= ~TLObject.FLAG_1;
                if (cell.rowspan > 1) cell.flags |= TLObject.FLAG_2; else cell.flags &= ~TLObject.FLAG_2;
            }
        }
    }

    public static String readPlainText(TL_iv.pageTableCell cell) {
        if (cell == null || cell.text == null) return "";
        return RichTextStyle.plainOf(cell.text);
    }

    public static CharSequence readStyledText(TL_iv.pageTableCell cell) {
        if (cell == null || cell.text == null) return "";
        return RichTextStyle.toSpannable(cell.text);
    }

    public static void applyPlainText(TL_iv.pageTableCell cell, String text) {
        final TL_iv.textPlain plain = new TL_iv.textPlain();
        plain.text = text == null ? "" : text;
        cell.text = plain;
        cell.flags |= TLObject.FLAG_7;
        if (cell.colspan > 1) cell.flags |= TLObject.FLAG_1; else cell.flags &= ~TLObject.FLAG_1;
        if (cell.rowspan > 1) cell.flags |= TLObject.FLAG_2; else cell.flags &= ~TLObject.FLAG_2;
    }

    public static void applyStyledText(TL_iv.pageTableCell cell, CharSequence styled) {
        cell.text = RichTextStyle.fromSpannable(styled);
        cell.flags |= TLObject.FLAG_7;
        if (cell.colspan > 1) cell.flags |= TLObject.FLAG_1; else cell.flags &= ~TLObject.FLAG_1;
        if (cell.rowspan > 1) cell.flags |= TLObject.FLAG_2; else cell.flags &= ~TLObject.FLAG_2;
    }

    private void rebuildAnchorList() {
        anchorsRowMajor.clear();
        LinkedHashSet<TL_iv.pageTableCell> seen = new LinkedHashSet<>();
        for (int r = 0; r < rowCount; r++) {
            for (int c = 0; c < colCount; c++) {
                if (isAnchor(r, c)) {
                    if (seen.add(grid[r][c])) {
                        anchorsRowMajor.add(grid[r][c]);
                    }
                }
            }
        }
    }

    private static TL_iv.pageTableCell[][] growCols(TL_iv.pageTableCell[][] src, int newCols) {
        TL_iv.pageTableCell[][] dst = new TL_iv.pageTableCell[src.length][newCols];
        for (int r = 0; r < src.length; r++) {
            System.arraycopy(src[r], 0, dst[r], 0, src[r].length);
        }
        return dst;
    }

    private static int[][] growIntCols(int[][] src, int newCols, int fill) {
        int[][] dst = new int[src.length][newCols];
        for (int r = 0; r < src.length; r++) {
            int oldLen = src[r].length;
            System.arraycopy(src[r], 0, dst[r], 0, oldLen);
            for (int c = oldLen; c < newCols; c++) dst[r][c] = fill;
        }
        return dst;
    }
}
