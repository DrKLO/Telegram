package org.telegram.ui.Components;

import android.graphics.Rect;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

public class PaddingItemDecoration extends RecyclerView.ItemDecoration {
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;

    public PaddingItemDecoration(int padding) {
        paddingLeft = paddingTop = paddingRight = paddingBottom = padding;
    }

    public PaddingItemDecoration(int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
        this.paddingLeft = paddingLeft;
        this.paddingTop = paddingTop;
        this.paddingRight = paddingRight;
        this.paddingBottom = paddingBottom;
    }

    public void setPadding(int paddingLeft, int paddingTop, int paddingRight, int paddingBottom) {
        this.paddingLeft = paddingLeft;
        this.paddingTop = paddingTop;
        this.paddingRight = paddingRight;
        this.paddingBottom = paddingBottom;
    }

    @Override
    public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                               RecyclerView.State state) {
        outRect.left = paddingLeft;
        outRect.top = paddingTop;
        outRect.right = paddingRight;
        outRect.bottom = paddingBottom;
    }
}