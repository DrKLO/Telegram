package org.Tajgram.ui.iv;

import org.Tajgram.tgnet.tl.TL_iv;

public class BlockRow {

    public TL_iv.PageBlock block;
    public int level;
    public int num;
    public MediaUploadState media;

    public BlockRow(TL_iv.PageBlock block) {
        this(block, 0, 0);
    }

    public BlockRow(TL_iv.PageBlock block, int level, int num) {
        this.block = block;
        this.level = level;
        this.num = num;
    }

    public boolean isInList() {
        return level > 0;
    }

    public boolean isOrdered() {
        return num > 0;
    }
}
