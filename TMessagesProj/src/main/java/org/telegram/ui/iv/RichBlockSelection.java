package org.telegram.ui.iv;

import android.graphics.Rect;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;

import org.telegram.ui.Cells.TextSelectionHelper;

/**
 * Builds a {@link TextSelectionHelper.TextLayoutBlock} for non-text blocks (divider, math, …) that
 * have no real text to select. The block carries a 1-char placeholder layout purely to satisfy the
 * selection machinery (length / offset bookkeeping); the actual selection-handle positions come from
 * {@link TextSelectionHelper.TextLayoutBlock#getSelectionBounds()}, whose bottom-left / bottom-right
 * corners the handles snap to. Bounds are passed in fresh on every call, so width / size changes are
 * handled for free without caching a layout.
 */
final class RichBlockSelection {

    private static Layout placeholder;

    private RichBlockSelection() {}

    private static Layout placeholder() {
        if (placeholder == null) {
            placeholder = new StaticLayout(" ", new TextPaint(), 1, Layout.Alignment.ALIGN_NORMAL, 1f, 0f, false);
        }
        return placeholder;
    }

    static TextSelectionHelper.TextLayoutBlock of(int left, int top, int right, int bottom) {
        final Rect bounds = new Rect(left, top, right, bottom);
        final Layout layout = placeholder();
        return new TextSelectionHelper.TextLayoutBlock() {
            @Override public Layout getLayout() { return layout; }
            @Override public int getX() { return 0; }
            @Override public int getY() { return 0; }
            @Override public int getRow() { return 0; }
            @Override public Rect getSelectionBounds() { return bounds; }
        };
    }
}
