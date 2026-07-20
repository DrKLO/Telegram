package org.telegram.ui.iv;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.text.TextPaint;
import android.view.View;

import org.telegram.messenger.LocaleController;
import org.telegram.ui.ActionBar.Theme;

/**
 * Shared gutter chrome for any rich block cell: the left/right inset for nesting depth, plus the list
 * marker (bullet / number / checkbox) drawn on the first block of a list item.
 *
 * <p>Composition, not inheritance — a cell owns an instance and calls it from {@code bind()} and its
 * draw pass. This works across the mixed {@code View} / {@code FrameLayout} cell hierarchy and keeps
 * the inset value cell-local, so a later drag animation can slide a block in and out of a container
 * by animating this value instead of fighting {@code ItemDecoration} offsets.</p>
 *
 * <p>Cross-row quote/list backgrounds do <b>not</b> live here — those span cells and are painted in
 * {@code RichEditorListView.dispatchDraw}.</p>
 */
public class RichBlockChrome {

    public static final int INDENT_DP_PER_LEVEL = 24;
    public static final int MARKER_WIDTH_DP = 28;
    /** Extra inset each additional quote nesting level adds — on <b>both</b> sides (the parent's bar + padding). */
    public static final int QUOTE_STEP_DP = 16;
    /** Left/right edge of the outermost quote background from the page margin; also the bar's left for depth 0. */
    public static final int QUOTE_GUTTER_DP = 16;
    public static final int QUOTE_BAR_DP = 3;
    /** Content inset from its innermost quote background: start side (past the bar) and end side. */
    public static final int QUOTE_PAD_L_DP = 12;
    public static final int QUOTE_PAD_R_DP = 8;
    /**
     * Top/bottom content padding (dp) on a quote's OUTER edge rows only. Chosen so the content sits ~8px inside
     * the quote background: {@code RichEditorListView.drawQuoteContainer} insets the background by {@code dp(2)}
     * from the row edge (top+2 / bottom-2), so a content padding of {@code 8 + 2 = 10} yields the same 8px gap
     * the single-block {@code pageBlockBlockquote} has. Interior blocks get no extra vpad, so between-block
     * spacing stays the normal paragraph spacing.
     */
    public static final int QUOTE_EDGE_VPAD_DP = 10;
    /**
     * Extra top/bottom inset a nested quote background gets per nesting level — the vertical mirror of
     * {@link #QUOTE_STEP_DP}. So when an inner quote begins/ends on the same block as its parent, the child
     * background doesn't share the exact same edge Y; it sits a few px inside. Kept small so it fits within the
     * {@link #QUOTE_EDGE_VPAD_DP} room the first/last block reserves (good for ~1–3 nesting levels).
     */
    public static final int QUOTE_NEST_VPAD_DP = 16;

    /**
     * Top/bottom room reserved on a quote's first/last block so the innermost quote background (which is inset
     * vertically by {@code depth * QUOTE_NEST_VPAD}) still sits ~8px outside the content — i.e. depth-aware edge
     * padding. Base {@link #QUOTE_EDGE_VPAD_DP} plus one nesting step per level of nesting, so the vertical gap
     * between a parent and child quote is on par with the horizontal {@link #QUOTE_STEP_DP}.
     */
    /**
     * Vertical edge room for {@code count} quote levels coinciding at an edge: base gap plus one nesting step per
     * additional coinciding level, so the innermost background sits ~8px outside the content and each coinciding
     * parent edge is separated by {@link #QUOTE_NEST_VPAD_DP}.
     */
    public static int quoteEdgePad(int count) {
        return count <= 0 ? 0 : dp(QUOTE_EDGE_VPAD_DP + (count - 1) * QUOTE_NEST_VPAD_DP);
    }

    /**
     * Vertical edge padding a cell uses at its TOP — scales with how many quotes START at this row
     * ({@code quoteTopEdge}), NOT absolute depth: a nested quote whose parent continues above only reserves the
     * base gap. 0 when this isn't a quote's first row (caller keeps its base padding). REPLACES base at the edge.
     */
    public static int quoteTopPad(BlockRow row) {
        return row == null ? 0 : quoteEdgePad(row.quoteTopEdge);
    }

    /** Vertical edge padding a cell uses at its BOTTOM — scales with the quotes ENDING at this row. */
    public static int quoteBottomPad(BlockRow row) {
        return row == null ? 0 : quoteEdgePad(row.quoteBottomEdge);
    }

    /** Content inset (px) for a block at the given list depth: content sits just past the marker column. */
    public static int insetForDepth(int depth) {
        if (depth <= 0) return 0;
        return dp(MARKER_WIDTH_DP + (depth - 1) * INDENT_DP_PER_LEVEL);
    }

    public static int quoteDepth(BlockRow row) {
        return row == null ? 0 : row.quoteIds.size();
    }

    /**
     * Start-side content inset (px) from quote nesting: for depth D the content sits {@code QUOTE_PAD_L} past the
     * innermost bar, which is itself {@code (D-1)*QUOTE_STEP} in from the outer margin. Reads {@code quoteIds}
     * (authoritative) so it is correct even before {@code assignContainers} re-runs.
     */
    public static int quoteInset(BlockRow row) {
        final int d = quoteDepth(row);
        return d <= 0 ? 0 : dp((d - 1) * QUOTE_STEP_DP + QUOTE_PAD_L_DP);
    }

    /** End-side content inset (px) from quote nesting — mirror of {@link #quoteInset} on the trailing edge. */
    public static int quoteInsetEnd(BlockRow row) {
        final int d = quoteDepth(row);
        return d <= 0 ? 0 : dp((d - 1) * QUOTE_STEP_DP + QUOTE_PAD_R_DP);
    }

    /**
     * Total start inset (px): quote nesting plus list depth. Uses the live {@code level}/{@code quoteIds}
     * fields (not the path) so a drag that changes depth updates the inset without waiting for a rebind.
     */
    public static int insetFor(BlockRow row) {
        return row == null ? 0 : quoteInset(row) + insetForDepth(Math.max(0, row.level));
    }

    /** Total end inset (px): quote nesting only (lists indent on the start side only). */
    public static int insetEndFor(BlockRow row) {
        return quoteInsetEnd(row);
    }

    public static boolean rtl() {
        return LocaleController.isRTL;
    }

    /**
     * Sets {@code cell}'s padding to its base plus the nesting inset — start side for lists, both sides for
     * quotes. Cells pass their own constant base padding so the inset composes with it.
     */
    public static void applyInset(View cell, BlockRow row, int baseLeft, int baseTop, int baseRight, int baseBottom) {
        applyInsetPx(cell, insetFor(row), insetEndFor(row), baseLeft, baseTop, baseRight, baseBottom);
    }

    /** Like {@link #applyInset} but with an explicit start inset (px) and no end inset — used by the animator. */
    public static void applyInsetPx(View cell, int inset, int baseLeft, int baseTop, int baseRight, int baseBottom) {
        applyInsetPx(cell, inset, 0, baseLeft, baseTop, baseRight, baseBottom);
    }

    /** Applies explicit start and end nesting insets (px) onto the base padding, honoring RTL. */
    public static void applyInsetPx(View cell, int startInset, int endInset, int baseLeft, int baseTop, int baseRight, int baseBottom) {
        if (rtl()) {
            cell.setPadding(baseLeft + endInset, baseTop, baseRight + startInset, baseBottom);
        } else {
            cell.setPadding(baseLeft + startInset, baseTop, baseRight + endInset, baseBottom);
        }
    }

    // --- marker drawing (bullet / number; checkbox is a non-interactive glyph for now) ---

    private final TextPaint markerPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);

    public RichBlockChrome() {
        markerPaint.setTextSize(dp(16));
        markerPaint.setTextAlign(Paint.Align.CENTER);
    }
}
