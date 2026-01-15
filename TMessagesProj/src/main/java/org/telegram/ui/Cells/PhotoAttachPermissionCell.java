/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ChatAttachAlertPhotoLayout;
import org.telegram.ui.Components.LayoutHelper;

public class PhotoAttachPermissionCell extends FrameLayout {

    private final Theme.ResourcesProvider resourcesProvider;
    private ImageView imageView;
    private ImageView imageView2;
    private TextView textView;
    private int itemSize;

    public PhotoAttachPermissionCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        imageView.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_attachPermissionImage), PorterDuff.Mode.MULTIPLY));
        addView(imageView, LayoutHelper.createFrame(44, 44, Gravity.CENTER, 5, 0, 0, 27));

        imageView2 = new ImageView(context);
        imageView2.setScaleType(ImageView.ScaleType.CENTER);
        imageView2.setColorFilter(new PorterDuffColorFilter(getThemedColor(Theme.key_chat_attachPermissionMark), PorterDuff.Mode.MULTIPLY));
        addView(imageView2, LayoutHelper.createFrame(44, 44, Gravity.CENTER, 5, 0, 0, 27));

        textView = new TextView(context);
        textView.setTextColor(getThemedColor(Theme.key_chat_attachPermissionText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        textView.setGravity(Gravity.CENTER);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 5, 13, 5, 0));

        itemSize = AndroidUtilities.dp(80);
    }

    public void setItemSize(int size) {
        itemSize = size;
    }

    public void setType(int type) {
        if (type == 0) {
            imageView.setImageResource(R.drawable.permissions_camera1);
            imageView2.setImageResource(R.drawable.permissions_camera2);
            textView.setText(LocaleController.getString(R.string.CameraPermissionText));

            imageView.setLayoutParams(LayoutHelper.createFrame(44, 44, Gravity.CENTER, 5, 0, 0, 27));
            imageView2.setLayoutParams(LayoutHelper.createFrame(44, 44, Gravity.CENTER, 5, 0, 0, 27));
        } else {
            imageView.setImageResource(R.drawable.permissions_gallery1);
            imageView2.setImageResource(R.drawable.permissions_gallery2);
            textView.setText(LocaleController.getString(R.string.GalleryPermissionText));

            imageView.setLayoutParams(LayoutHelper.createFrame(44, 44, Gravity.CENTER, 0, 0, 2, 27));
            imageView2.setLayoutParams(LayoutHelper.createFrame(44, 44, Gravity.CENTER, 0, 0, 2, 27));
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(
            MeasureSpec.makeMeasureSpec(itemSize, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(itemSize + AndroidUtilities.dp(ChatAttachAlertPhotoLayout.GAP), MeasureSpec.EXACTLY));
    }

    private int getThemedColor(int key) {
        return Theme.getColor(key, resourcesProvider);
    }
}
