/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.io.File;

@SuppressLint("NewApi")
public class PhotoAttachCameraCell extends FrameLayout {

    private ImageView imageView;
    private ImageView backgroundView;
    private int itemSize;

    public PhotoAttachCameraCell(Context context) {
        super(context);

        backgroundView = new ImageView(context);
        backgroundView.setScaleType(ImageView.ScaleType.CENTER_CROP);
        //backgroundView.setAdjustViewBounds(false);
        addView(backgroundView, LayoutHelper.createFrame(80, 80));

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setImageResource(R.drawable.instant_camera);
        addView(imageView, LayoutHelper.createFrame(80, 80));
        setFocusable(true);

        itemSize = AndroidUtilities.dp(0);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(5), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(5), MeasureSpec.EXACTLY));
    }

    public void setItemSize(int size) {
        itemSize = size;

        LayoutParams layoutParams = (LayoutParams) imageView.getLayoutParams();
        layoutParams.width = layoutParams.height = itemSize;

        layoutParams = (LayoutParams) backgroundView.getLayoutParams();
        layoutParams.width = layoutParams.height = itemSize;
    }

    public ImageView getImageView() {
        return imageView;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        imageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_dialogCameraIcon), PorterDuff.Mode.MULTIPLY));
    }

    public void updateBitmap() {
        Bitmap bitmap = null;
        try {
            File file = new File(ApplicationLoader.getFilesDirFixed(), "cthumb.jpg");
            bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
        } catch (Throwable ignore) {

        }
        if (bitmap != null) {
            backgroundView.setImageBitmap(bitmap);
        } else {
            backgroundView.setImageResource(R.drawable.icplaceholder);
        }
    }
}
