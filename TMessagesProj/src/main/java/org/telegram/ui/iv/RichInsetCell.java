package org.telegram.ui.iv;

/**
 * A cell whose nesting inset can be re-applied (and animated) after a structural move changes its block's depth,
 * without a full rebind. Implemented by the non-text block cells that own a {@link RichBlockInset}.
 */
interface RichInsetCell {
    /** Re-read the bound row's depth and re-apply the inset, animating the change when {@code animated}. */
    void resyncBlockInset(boolean animated);
}
