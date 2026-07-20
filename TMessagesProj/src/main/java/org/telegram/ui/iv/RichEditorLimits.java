package org.telegram.ui.iv;

import org.telegram.messenger.AppGlobalConfig;
import org.telegram.tgnet.tl.TL_iv;

import java.util.ArrayList;

// Measures rich-message content against the server-defined limits (see AppGlobalConfig.richMessage*).
public class RichEditorLimits {

    public static final int LIMIT_NONE = 0;
    public static final int LIMIT_LENGTH = 1;
    public static final int LIMIT_BLOCKS = 2;
    public static final int LIMIT_DEPTH = 3;
    public static final int LIMIT_MEDIA = 4;
    public static final int LIMIT_TABLE_COLS = 5;

    public int length;
    public int blocks;
    public int depth;
    public int media;
    public int tableCols;

    // Measures the flattened send structure plus the count of medias added in the editor.
    public static RichEditorLimits measure(ArrayList<TL_iv.PageBlock> top, int mediaCount) {
        final RichEditorLimits m = new RichEditorLimits();
        m.media = mediaCount;
        m.blocks += top.size();
        for (int i = 0; i < top.size(); i++) {
            measureBlock(top.get(i), 1, m);
        }
        return m;
    }

    // The first server limit this content exceeds, or LIMIT_NONE when it fits.
    public int findExceeded(AppGlobalConfig config) {
        if (length > config.richMessageLengthLimit.get()) return LIMIT_LENGTH;
        if (blocks > config.richMessageMaxBlocks.get()) return LIMIT_BLOCKS;
        if (depth > config.richMessageMaxDepth.get()) return LIMIT_DEPTH;
        if (media > config.richMessageMaxMedia.get()) return LIMIT_MEDIA;
        if (tableCols > config.richMessageMaxTableCols.get()) return LIMIT_TABLE_COLS;
        return LIMIT_NONE;
    }

    public boolean isWithin(AppGlobalConfig config) {
        return findExceeded(config) == LIMIT_NONE;
    }

    private static void measureBlock(TL_iv.PageBlock b, int depth, RichEditorLimits m) {
        if (b == null) return;
        if (depth > m.depth) m.depth = depth;

        addText(b.text, depth, m);

        if (b instanceof TL_iv.pageBlockBlockquote) {
            addText(((TL_iv.pageBlockBlockquote) b).caption, depth, m);
        } else if (b instanceof TL_iv.pageBlockPullquote) {
            addText(((TL_iv.pageBlockPullquote) b).caption, depth, m);
        } else if (b instanceof TL_iv.pageBlockBlockquoteBlocks) {
            final TL_iv.pageBlockBlockquoteBlocks q = (TL_iv.pageBlockBlockquoteBlocks) b;
            addText(q.caption, depth, m);
            m.blocks += q.blocks.size();
            for (int i = 0; i < q.blocks.size(); i++) measureBlock(q.blocks.get(i), depth + 1, m);
        } else if (b instanceof TL_iv.pageBlockDetails) {
            final TL_iv.pageBlockDetails d = (TL_iv.pageBlockDetails) b;
            addText(d.title, depth, m);
            m.blocks += d.blocks.size();
            for (int i = 0; i < d.blocks.size(); i++) measureBlock(d.blocks.get(i), depth + 1, m);
        } else if (b instanceof TL_iv.pageBlockList) {
            final TL_iv.pageBlockList l = (TL_iv.pageBlockList) b;
            m.blocks += l.items.size();
            for (int i = 0; i < l.items.size(); i++) measureListItem(l.items.get(i), depth + 1, m);
        } else if (b instanceof TL_iv.pageBlockOrderedList) {
            final TL_iv.pageBlockOrderedList l = (TL_iv.pageBlockOrderedList) b;
            m.blocks += l.items.size();
            for (int i = 0; i < l.items.size(); i++) measureOrderedItem(l.items.get(i), depth + 1, m);
        } else if (b instanceof TL_iv.pageBlockTable) {
            final TL_iv.pageBlockTable t = (TL_iv.pageBlockTable) b;
            addText(t.title, depth, m);
            if (t.rows != null) {
                m.blocks += t.rows.size();
                for (int r = 0; r < t.rows.size(); r++) {
                    final TL_iv.pageTableRow row = t.rows.get(r);
                    int cols = 0;
                    if (row.cells != null) {
                        for (int c = 0; c < row.cells.size(); c++) {
                            final TL_iv.pageTableCell cell = row.cells.get(c);
                            cols += TableModel.spanCol(cell);
                            addText(cell.text, depth + 1, m);
                        }
                    }
                    if (cols > m.tableCols) m.tableCols = cols;
                }
            }
        } else if (b instanceof TL_iv.pageBlockCollage) {
            addCaption(b.caption, depth, m);
            final ArrayList<TL_iv.PageBlock> items = ((TL_iv.pageBlockCollage) b).items;
            for (int i = 0; i < items.size(); i++) measureBlock(items.get(i), depth + 1, m);
        } else if (b instanceof TL_iv.pageBlockSlideshow) {
            addCaption(b.caption, depth, m);
            final ArrayList<TL_iv.PageBlock> items = ((TL_iv.pageBlockSlideshow) b).items;
            for (int i = 0; i < items.size(); i++) measureBlock(items.get(i), depth + 1, m);
        } else if (b instanceof TL_iv.pageBlockMath) {
            final String src = ((TL_iv.pageBlockMath) b).source;
            if (src != null) m.length += src.length();
        } else {
            // photo / video / audio / map carry a PageCaption (text + credit)
            addCaption(b.caption, depth, m);
        }
    }

    private static void measureListItem(TL_iv.PageListItem item, int depth, RichEditorLimits m) {
        if (item instanceof TL_iv.TL_pageListItemText) {
            addText(((TL_iv.TL_pageListItemText) item).text, depth, m);
        } else if (item instanceof TL_iv.TL_pageListItemBlocks) {
            final ArrayList<TL_iv.PageBlock> blocks = ((TL_iv.TL_pageListItemBlocks) item).blocks;
            for (int i = 0; i < blocks.size(); i++) measureBlock(blocks.get(i), depth, m);
        }
    }

    private static void measureOrderedItem(TL_iv.PageListOrderedItem item, int depth, RichEditorLimits m) {
        if (item instanceof TL_iv.TL_pageListOrderedItemText) {
            addText(((TL_iv.TL_pageListOrderedItemText) item).text, depth, m);
        } else if (item instanceof TL_iv.TL_pageListOrderedItemBlocks) {
            final ArrayList<TL_iv.PageBlock> blocks = ((TL_iv.TL_pageListOrderedItemBlocks) item).blocks;
            for (int i = 0; i < blocks.size(); i++) measureBlock(blocks.get(i), depth, m);
        }
    }

    private static void addCaption(TL_iv.PageCaption caption, int depth, RichEditorLimits m) {
        if (caption == null) return;
        addText(caption.text, depth, m);
        addText(caption.credit, depth, m);
    }

    private static void addText(TL_iv.RichText rt, int depth, RichEditorLimits m) {
        if (rt == null) return;
        m.length += RichTextStyle.plainOf(rt).length();
        final int d = depth + richTextDepth(rt);
        if (d > m.depth) m.depth = d;
    }

    private static int richTextDepth(TL_iv.RichText rt) {
        if (rt == null
            || rt instanceof TL_iv.textEmpty
            || rt instanceof TL_iv.textPlain
            || rt instanceof TL_iv.textCustomEmoji) {
            return 0;
        }
        if (rt instanceof TL_iv.textConcat) {
            int max = 0;
            for (TL_iv.RichText child : ((TL_iv.textConcat) rt).texts) {
                max = Math.max(max, richTextDepth(child));
            }
            return max;
        }
        return 1 + richTextDepth(rt.text);
    }
}
