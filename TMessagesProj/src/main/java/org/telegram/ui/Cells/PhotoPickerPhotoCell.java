/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;

public class PhotoPickerPhotoCell extends FrameLayout {

    public BackupImageView imageView;
    public FrameLayout checkFrame;
    public CheckBox2 checkBox;
    public TextView videoTextView;
    public FrameLayout videoInfoContainer;
    private int itemWidth;
    private int extraWidth;
    private Paint backgroundPaint = new Paint();

    public PhotoPickerPhotoCell(Context context) {
        super(context);
        setWillNotDraw(false);

        imageView = new BackupImageView(context);
        imageView.setRoundRadius(AndroidUtilities.dp(4));
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        checkFrame = new FrameLayout(context);
        addView(checkFrame, LayoutHelper.createFrame(42, 42, Gravity.RIGHT | Gravity.TOP));

        videoInfoContainer = new FrameLayout(context) {

            private Path path = new Path();
            float[] radii = new float[8];
            private RectF rect = new RectF();
            private Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

            @Override
            protected void onDraw(Canvas canvas) {
                rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                radii[0] = radii[1] = radii[2] = radii[3] = 0;
                radii[4] = radii[5] = radii[6] = radii[7] = AndroidUtilities.dp(4);
                path.reset();
                path.addRoundRect(rect, radii, Path.Direction.CW);
                path.close();
                paint.setColor(0x7f000000);
                canvas.drawPath(path, paint);
            }
        };
        videoInfoContainer.setWillNotDraw(false);
        videoInfoContainer.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
        addView(videoInfoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.BOTTOM | Gravity.LEFT));

        ImageView imageView1 = new ImageView(context);
        imageView1.setImageResource(R.drawable.ic_video);
        videoInfoContainer.addView(imageView1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

        videoTextView = new TextView(context);
        videoTextView.setTextColor(0xffffffff);
        videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        videoTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        videoInfoContainer.addView(videoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, -0.7f, 0, 0));

        checkBox = new CheckBox2(context, 24);
        checkBox.setDrawBackgroundAsArc(11);
        checkBox.setColor(Theme.key_chat_attachCheckBoxBackground, Theme.key_chat_attachPhotoBackground, Theme.key_chat_attachCheckBoxCheck);
        addView(checkBox, LayoutHelper.createFrame(26, 26, Gravity.LEFT | Gravity.TOP, 55, 4, 0, 0));
        checkBox.setVisibility(VISIBLE);

        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth + extraWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        updateColors();
    }

    public void setItemWidth(int width, int extra) {
        itemWidth = width;
        extraWidth = extra;

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) checkFrame.getLayoutParams();
        layoutParams.rightMargin = extra;

        layoutParams = (FrameLayout.LayoutParams) imageView.getLayoutParams();
        layoutParams.rightMargin = extra;

        layoutParams = (FrameLayout.LayoutParams) videoInfoContainer.getLayoutParams();
        layoutParams.rightMargin = extra;
    }

    public void updateColors() {
        checkBox.setColor(Theme.key_chat_attachCheckBoxBackground, Theme.key_chat_attachPhotoBackground, Theme.key_chat_attachCheckBoxCheck);
    }

    public void setNum(int num) {
        checkBox.setNum(num);
    }

    public void setImage(MediaController.PhotoEntry photoEntry) {
        Drawable thumb = getResources().getDrawable(R.drawable.nophotos);
        if (photoEntry.thumbPath != null) {
            imageView.setImage(photoEntry.thumbPath, null, thumb);
        } else if (photoEntry.path != null) {
            imageView.setOrientation(photoEntry.orientation, true);
            if (photoEntry.isVideo) {
                videoInfoContainer.setVisibility(View.VISIBLE);
                videoTextView.setText(AndroidUtilities.formatShortDuration(photoEntry.duration));
                setContentDescription(LocaleController.getString("AttachVideo", R.string.AttachVideo) + ", " + LocaleController.formatDuration(photoEntry.duration));
                imageView.setImage("vthumb://" + photoEntry.imageId + ":" + photoEntry.path, null, thumb);
            } else {
                videoInfoContainer.setVisibility(View.INVISIBLE);
                setContentDescription(LocaleController.getString("AttachPhoto", R.string.AttachPhoto));
                imageView.setImage("thumb://" + photoEntry.imageId + ":" + photoEntry.path, null, thumb);
            }
        } else {
            imageView.setImageDrawable(thumb);
        }
    }

    public void setImage(MediaController.SearchImage searchImage) {
        Drawable thumb = getResources().getDrawable(R.drawable.nophotos);
        if (searchImage.thumbPhotoSize != null) {
            imageView.setImage(ImageLocation.getForPhoto(searchImage.thumbPhotoSize, searchImage.photo), null, thumb, searchImage);
        } else if (searchImage.photoSize != null) {
            imageView.setImage(ImageLocation.getForPhoto(searchImage.photoSize, searchImage.photo), "80_80", thumb, searchImage);
        } else if (searchImage.thumbPath != null) {
            imageView.setImage(searchImage.thumbPath, null, thumb);
        } else if (searchImage.thumbUrl != null && searchImage.thumbUrl.length() > 0) {
            imageView.setImage(searchImage.thumbUrl, null, thumb);
        } else if (MessageObject.isDocumentHasThumb(searchImage.document)) {
            TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(searchImage.document.thumbs, 320);
            imageView.setImage(ImageLocation.getForDocument(photoSize, searchImage.document), null, thumb, searchImage);
        } else {
            imageView.setImageDrawable(thumb);
        }
    }

    public void setChecked(final int num, final boolean checked, final boolean animated) {
        checkBox.setChecked(num, checked, animated);
    }
}
