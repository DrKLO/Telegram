/*
 * This is the source code of Tajgram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.Tajgram.ui.Cells;

import android.content.Context;
import android.view.View;

import org.Tajgram.messenger.AndroidUtilities;
import org.Tajgram.ui.Components.ChatAttachAlertPhotoLayout;

public class PhotoAttachCameraCell extends View {
    private int itemSize;

    public PhotoAttachCameraCell(Context context) {
        super(context);
        setFocusable(true);
        itemSize = AndroidUtilities.dp(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(ChatAttachAlertPhotoLayout.GAP), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(ChatAttachAlertPhotoLayout.GAP), MeasureSpec.EXACTLY));
    }

    public void setItemSize(int size) {
        itemSize = size;
    }
}
