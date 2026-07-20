package org.telegram.ui.iv;

import org.telegram.tgnet.tl.TL_iv;

import java.util.ArrayList;

public class BlockRow {

    private static long ID_GEN = 1;

    public final long id;

    public TL_iv.PageBlock block;
    public int level;
    public int num;
    public boolean checkbox;
    public boolean checked;
    public MediaUploadState media;
    public ArrayList<MediaUploadState> medias;
    public boolean detailsEnd;

    public final ArrayList<RichContainer> path = new ArrayList<>();

    public final ArrayList<Long> quoteIds = new ArrayList<>();

    public boolean itemStart;

    public int quoteTopEdge;
    public int quoteBottomEdge;
    public boolean quoteFirst;
    public boolean quoteLast;

    // Hint context, set during RichEditorListView rebuild and read by RichTextCell.getHint():
    // firstBlock — this row is the first block of the whole editor (heading1 → "Title" hint);
    // singleParagraph — the editor contains exactly one paragraph block ("Type something..." hint).
    public boolean firstBlock;
    public boolean singleParagraph;

    public long authorQuoteId;

    public BlockRow(TL_iv.PageBlock block) {
        this(block, 0, 0);
    }

    public BlockRow(TL_iv.PageBlock block, int level, int num) {
        this(block, level, num, ID_GEN++);
    }

    public BlockRow(TL_iv.PageBlock block, int level, int num, long id) {
        this.block = block;
        this.level = level;
        this.num = num;
        this.id = id;
    }

    public boolean isInList() {
        return level > 0;
    }

    public boolean isOrdered() {
        return num > 0;
    }

    public boolean isChecklist() {
        return level > 0 && checkbox;
    }

    public RichContainer innermostQuote() {
        for (int i = path.size() - 1; i >= 0; i--) if (path.get(i).isQuote()) return path.get(i);
        return null;
    }

    public boolean isInQuote() {
        return innermostQuote() != null;
    }
}
