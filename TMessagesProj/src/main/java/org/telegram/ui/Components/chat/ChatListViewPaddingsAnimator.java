package org.telegram.ui.Components.chat;

import androidx.recyclerview.widget.RecyclerView;

public class ChatListViewPaddingsAnimator {
    private final RecyclerView recyclerView;

    public ChatListViewPaddingsAnimator(RecyclerView recyclerView) {
        this.recyclerView = recyclerView;
    }

    private int currentAdditionalHeight;
    public void setPaddings(
        int paddingTopTarget,
        float paddingBottomAnimated, int paddingBottomTarget
    ) {
        final float translationY = paddingBottomTarget - paddingBottomAnimated;
        final int additionalHeight = 0; //(int) Math.ceil(Math.abs(translationY));

        if (additionalHeight == 0 && currentAdditionalHeight != 0) {
            currentAdditionalHeight = 0;
            recyclerView.requestLayout();
        } else if (additionalHeight > currentAdditionalHeight) {
            currentAdditionalHeight = additionalHeight;
            recyclerView.requestLayout();
        }

        final int paddingTop = paddingTopTarget; // + currentAdditionalHeight;
        final int paddingBottom = (int) paddingBottomAnimated; // paddingBottomTarget;

        final int paddingTopOld = recyclerView.getPaddingTop();
        final int paddingBottomOld = recyclerView.getPaddingBottom();

        //recyclerView.setTranslationY(translationY);
        if (paddingTopOld != paddingTop || paddingBottomOld != paddingBottom) {
            recyclerView.setPadding(
                    recyclerView.getPaddingLeft(),
                    paddingTop,
                    recyclerView.getPaddingRight(),
                    paddingBottom
            );
        }
    }

    public int getCurrentAdditionalHeight() {
        return currentAdditionalHeight;
    }
}
