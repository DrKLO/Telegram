package org.telegram.ui.iv;

import android.content.Context;
import android.widget.FrameLayout;

/**
 * Base for every content rich block cell. Centralizes the cross-cell chrome that was copy-pasted into each
 * cell:
 * <ul>
 *   <li>the <b>nesting inset</b> (quote + list depth) on the start side, animated when the same block's depth
 *       changes on a drag — replacing the per-cell {@link RichBlockInset} field + {@code applyBlockInset} +
 *       {@code resyncBlockInset} trio and the {@link RichInsetCell} plumbing;</li>
 *   <li>(later) the quote <b>author</b> edit text, so any block can carry a quote author without every cell
 *       re-implementing the edit surface and its routing.</li>
 * </ul>
 *
 * <p>Cells keep their own {@code onMeasure}/{@code onLayout} (each lays out very different content); the base
 * only owns the inset value and, later, author placement helpers.</p>
 *
 * <p><b>Two inset modes.</b> By default the inset is folded into the view's start <b>padding</b> (composing
 * with the cell's constant base padding declared via {@link #setBlockPadding}) — good for cells whose content
 * should simply shift. Cells that instead manage the inset inside their own layout/draw (e.g. a horizontally
 * scrolling child, or a {@code View} that paints a region) override {@link #onBlockInsetChanged} to store and
 * invalidate, and read {@link #blockInset()} where they lay out — they must not fold it into padding.</p>
 */
public abstract class RichBlockCell extends FrameLayout implements RichInsetCell {

    protected BlockRow currentRow;

    private final RichBlockInset insetAnim = new RichBlockInset();
    private int blockInset;
    private int basePadLeft, basePadTop, basePadRight, basePadBottom;

    public RichBlockCell(Context context) {
        super(context);
    }

    /**
     * Declares the cell's constant base padding. The nesting inset composes onto the start side (right in RTL)
     * on top of this. Call once from the constructor; padding-mode cells rely on it, field-mode cells that
     * override {@link #onBlockInsetChanged} can skip it.
     */
    protected void setBlockPadding(int left, int top, int right, int bottom) {
        basePadLeft = left;
        basePadTop = top;
        basePadRight = right;
        basePadBottom = bottom;
        RichBlockChrome.applyInsetPx(this, blockInset, basePadLeft, basePadTop, basePadRight, basePadBottom);
    }

    /** Current nesting inset (px, start side). Field-mode cells read this from their own measure/layout/draw. */
    protected int blockInset() {
        return blockInset;
    }

    /** Applies the inset value from the animator. Stores it, then dispatches to {@link #onBlockInsetChanged}. */
    private void applyBlockInset(int px) {
        blockInset = px;
        onBlockInsetChanged(px);
    }

    /**
     * Reacts to a new inset value. Default folds it into the start padding using the declared base padding.
     * Field-mode cells override to {@code requestLayout()}/{@code invalidate()} and consume {@link #blockInset()}
     * themselves — such overrides must <b>not</b> call {@code super}.
     */
    /**
     * Extra horizontal margin a cell needs on BOTH sides when it's nested in a quote/list. Edge-to-edge cells
     * (media, map — base padding 0, drawn full width) return the page margin here so their content lines up with
     * text cells (which already carry a {@code dp(16)} base margin) inside the quote background. 0 at top level.
     */
    protected int nestedContentMargin() {
        return 0;
    }

    protected void onBlockInsetChanged(int px) {
        // Quote nesting insets horizontally (start/end) and, on the quote's first/last block, adds the depth-aware
        // vertical edge room so the content sits inside the quote background — same as RichTextCell does for text.
        final int endInset = RichBlockChrome.insetEndFor(currentRow);
        final int margin = (px > 0 || endInset > 0) ? nestedContentMargin() : 0; // add page margin only when nested
        // At a quote edge the edge padding REPLACES the cell's base top/bottom padding (it is not added to it);
        // interior/non-quote blocks keep their base padding.
        final int top = currentRow != null && currentRow.quoteFirst ? RichBlockChrome.quoteTopPad(currentRow) : basePadTop;
        final int bottom = currentRow != null && currentRow.quoteLast ? RichBlockChrome.quoteBottomPad(currentRow) : basePadBottom;
        RichBlockChrome.applyInsetPx(this, px + margin, endInset + margin, basePadLeft, top, basePadRight, bottom);
    }

    /**
     * Applies the bound row's inset. Call from the cell's {@code bind}. Snaps when a different block is now bound
     * (view recycling) and animates only when the same block's depth changed — {@link RichBlockInset} self-guards.
     */
    protected void bindBlockInset(BlockRow row) {
        insetAnim.apply(row, this::applyBlockInset);
    }

    @Override
    public void resyncBlockInset(boolean animated) {
        insetAnim.apply(currentRow, this::applyBlockInset, animated);
    }
}
