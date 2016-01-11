/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;

public class PhotoAttachCameraCell extends FrameLayout {

    public PhotoAttachCameraCell(Context context) {
        super(context);

        ImageView imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        //imageView.setImageResource(R.drawable.ic_attach_photobig);
        imageView.setBackgroundColor(0xff777777);
        addView(imageView, LayoutHelper.createFrame(80, 80));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(86), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
    }
}
