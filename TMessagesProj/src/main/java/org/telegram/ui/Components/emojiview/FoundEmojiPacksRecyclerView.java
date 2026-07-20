package org.telegram.ui.Components.emojiview;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalRecyclerView;

import java.util.ArrayList;

@SuppressLint("ViewConstructor")
public class FoundEmojiPacksRecyclerView extends UniversalRecyclerView {
    private static final int SCROLL_MARGIN = 92;

    public FoundEmojiPacksRecyclerView(Context context, int currentAccount, int classGuid, boolean dialog, Utilities.Callback2<ArrayList<UItem>, UniversalAdapter> fillItems, Utilities.Callback5<UItem, View, Integer, Float, Float> onClick, Utilities.Callback5Return<UItem, View, Integer, Float, Float, Boolean> onLongClick, Theme.ResourcesProvider resourcesProvider, int spansCount, int orientation) {
        super(context, currentAccount, classGuid, dialog, fillItems, onClick, onLongClick, resourcesProvider, spansCount, orientation);
        setAdaptiveOverScroll();
    }

    public void scrollOnSelect(View view) {
        if (view == null) {
            return;
        }

        final float lMargin = dp(SCROLL_MARGIN);
        final float rMargin = getWidth() - lMargin;

        final float left = view.getX();
        final float right = left + view.getWidth();

        final int scrollBy;

        if (left < lMargin) {
            scrollBy = (int) (left - lMargin);
        } else if (right > rMargin) {
            scrollBy = (int) (right - rMargin);
        } else {
            scrollBy = 0;
        }
        if (scrollBy != 0) {
            AndroidUtilities.doOnLayout(this, () -> {
                view.postOnAnimation(() -> {
                    smoothScrollBy(scrollBy, 0);
                });
            });
        }
    }
}
