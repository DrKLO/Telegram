package org.telegram.ui.iv;

import org.telegram.tgnet.tl.TL_iv;

/**
 * One enclosing container in a block's nesting path.
 *
 * A {@link BlockRow} carries an ordered list of these (outermost first) describing every
 * list / quote / details it is nested inside. This is the single representation for arbitrary
 * nesting ("quote inside a list inside a quote") that a flat {@code int level} cannot encode.
 *
 * <p>{@link #id} is the identity of a <b>container instance</b>. Two adjacent rows carrying a
 * quote container with the same id belong to the <b>same</b> quote; different ids mean two
 * separate quotes that happen to be adjacent. This identity is what lets rendering group rows
 * into a single bar/background and lets outdent split one container into two (the split tail
 * gets a fresh id so it will not re-merge).</p>
 *
 * <p>For lists, {@link #itemId} additionally groups rows into a single list <i>item</i>: a bullet
 * may own several blocks (a paragraph, then a nested quote, then a table) — they share one
 * {@code (id, itemId)} and only the first row of that item draws the marker.</p>
 */
public final class RichContainer {

    public static final int LIST = 0;
    public static final int QUOTE = 1;
    public static final int DETAILS = 2;

    private static long ID_GEN = 1;
    public static long newId() { return ID_GEN++; }

    public final int type;
    /** Identity of this container instance (grouping adjacency + split correctness). */
    public long id;

    // --- LIST ---
    /** ordered (numbered) vs bullet list. */
    public boolean ordered;
    /** checkbox (task) list. */
    public boolean checklist;
    /** Identity of the list item this block belongs to; groups multi-block items. */
    public long itemId;
    /** 1-based ordinal of the owning item within this list (for ordered lists / display). */
    public int itemNum;
    /** Checkbox state for the owning item (per item, not per row). */
    public boolean itemChecked;

    // --- QUOTE ---
    /** Optional trailing author of the quote. */
    public TL_iv.RichText author;

    // --- DETAILS ---
    /** Expanded/collapsed state. */
    public boolean open;

    public RichContainer(int type, long id) {
        this.type = type;
        this.id = id;
    }

    public static RichContainer list(long id, long itemId, boolean ordered, boolean checklist, boolean checked) {
        final RichContainer c = new RichContainer(LIST, id);
        c.itemId = itemId;
        c.ordered = ordered;
        c.checklist = checklist;
        c.itemChecked = checked;
        return c;
    }

    public static RichContainer quote(long id) {
        return new RichContainer(QUOTE, id);
    }

    public static RichContainer details(long id, boolean open) {
        final RichContainer c = new RichContainer(DETAILS, id);
        c.open = open;
        return c;
    }

    public boolean isList() { return type == LIST; }
    public boolean isQuote() { return type == QUOTE; }
    public boolean isDetails() { return type == DETAILS; }

    /** Same container instance — same type and id, ignoring per-item fields. */
    public boolean sameInstance(RichContainer o) {
        return o != null && o.type == type && o.id == id;
    }

    /** Same instance and same item — for grouping rows of one multi-block list item. */
    public boolean sameItem(RichContainer o) {
        return sameInstance(o) && o.itemId == itemId;
    }

    public RichContainer copy() {
        final RichContainer c = new RichContainer(type, id);
        c.ordered = ordered;
        c.checklist = checklist;
        c.itemId = itemId;
        c.itemChecked = itemChecked;
        c.author = author;
        c.open = open;
        return c;
    }
}
