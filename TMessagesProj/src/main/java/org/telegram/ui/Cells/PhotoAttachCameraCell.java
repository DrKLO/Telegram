/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.view.View;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout;

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
