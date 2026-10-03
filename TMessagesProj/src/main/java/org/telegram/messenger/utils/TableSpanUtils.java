package org.telegram.messenger.utils;

import org.telegram.tgnet.tl.TL_iv;

import java.util.List;

public final class TableSpanUtils {

    public static final int MAX_ADDITIONAL_CELLS = 1000;

    private TableSpanUtils() {
    }

    public static boolean CanUseSpans(List<TL_iv.pageTableRow> rows) {
        long remaining = MAX_ADDITIONAL_CELLS;
        for (TL_iv.pageTableRow row : rows) {
            for (TL_iv.pageTableCell cell : row.cells) {
                long additionalCells = (long) Math.max(1, cell.colspan) * Math.max(1, cell.rowspan) - 1;
                if (additionalCells > remaining) {
                    return false;
                }
                remaining -= additionalCells;
            }
        }
        return true;
    }

}
