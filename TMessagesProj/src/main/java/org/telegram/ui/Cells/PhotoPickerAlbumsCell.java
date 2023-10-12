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
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;

public class PhotoPickerAlbumsCell extends FrameLayout {

    public interface PhotoPickerAlbumsCellDelegate {
        void didSelectAlbum(MediaController.AlbumEntry albumEntry);
    }

    private AlbumView[] albumViews;
    private MediaController.AlbumEntry[] albumEntries;
    private int albumsCount;
    private PhotoPickerAlbumsCellDelegate delegate;
    private Paint backgroundPaint = new Paint();

    private class AlbumView extends FrameLayout {

        private BackupImageView imageView;
        private TextView nameTextView;
        private TextView countTextView;
        private View selector;

        public AlbumView(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            linearLayout.setBackgroundResource(R.drawable.album_shadow);
            addView(linearLayout, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 60, Gravity.LEFT | Gravity.BOTTOM));

            nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            nameTextView.setTextColor(0xffffffff);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setMaxLines(1);
            nameTextView.setGravity(Gravity.BOTTOM);
            linearLayout.addView(nameTextView, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1.0f, 8, 0, 0, 5));

            countTextView = new TextView(context);
            countTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            countTextView.setTextColor(0xffffffff);
            countTextView.setSingleLine(true);
            countTextView.setEllipsize(TextUtils.TruncateAt.END);
            countTextView.setMaxLines(1);
            countTextView.setGravity(Gravity.BOTTOM);
            linearLayout.addView(countTextView, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, 4, 0, 7, 5));

            selector = new View(context);
            selector.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (Build.VERSION.SDK_INT >= 21) {
                selector.drawableHotspotChanged(event.getX(), event.getY());
            }
            return super.onTouchEvent(event);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!imageView.getImageReceiver().hasNotThumb() || imageView.getImageReceiver().getCurrentAlpha() != 1.0f) {
                backgroundPaint.setColor(Theme.getColor(Theme.key_chat_attachPhotoBackground));
                canvas.drawRect(0, 0, imageView.getMeasuredWidth(), imageView.getMeasuredHeight(), backgroundPaint);
            }
        }
    }

    public PhotoPickerAlbumsCell(Context context) {
        super(context);
        albumEntries = new MediaController.AlbumEntry[4];
        albumViews = new AlbumView[4];
        for (int a = 0; a < 4; a++) {
            albumViews[a] = new AlbumView(context);
            addView(albumViews[a]);
            albumViews[a].setVisibility(INVISIBLE);
            albumViews[a].setTag(a);
            albumViews[a].setOnClickListener(v -> {
                if (delegate != null) {
                    delegate.didSelectAlbum(albumEntries[(Integer) v.getTag()]);
                }
            });
        }
    }

    public void setAlbumsCount(int count) {
        for (int a = 0; a < albumViews.length; a++) {
            albumViews[a].setVisibility(a < count ? VISIBLE : INVISIBLE);
        }
        albumsCount = count;
    }

    public void setDelegate(PhotoPickerAlbumsCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setAlbum(int a, MediaController.AlbumEntry albumEntry) {
        albumEntries[a] = albumEntry;

        if (albumEntry != null) {
            AlbumView albumView = albumViews[a];
            albumView.imageView.setOrientation(0, true);
            if (albumEntry.coverPhoto != null && albumEntry.coverPhoto.path != null) {
                albumView.imageView.setOrientation(albumEntry.coverPhoto.orientation, albumEntry.coverPhoto.invert, true);
                if (albumEntry.coverPhoto.isVideo) {
                    albumView.imageView.setImage("vthumb://" + albumEntry.coverPhoto.imageId + ":" + albumEntry.coverPhoto.path, null, Theme.chat_attachEmptyDrawable);
                } else {
                    albumView.imageView.setImage("thumb://" + albumEntry.coverPhoto.imageId + ":" + albumEntry.coverPhoto.path, null, Theme.chat_attachEmptyDrawable);
                }
            } else {
                albumView.imageView.setImageDrawable(Theme.chat_attachEmptyDrawable);
            }
            albumView.nameTextView.setText(albumEntry.bucketName);
            albumView.countTextView.setText(String.format("%d", albumEntry.photos.size()));
        } else {
            albumViews[a].setVisibility(INVISIBLE);
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int itemWidth;
        if (AndroidUtilities.isTablet()) {
            itemWidth = (AndroidUtilities.dp(490) - AndroidUtilities.dp(12) - (albumsCount - 1) * AndroidUtilities.dp(4)) / albumsCount;
        } else {
            itemWidth = (AndroidUtilities.displaySize.x - AndroidUtilities.dp(12) - (albumsCount - 1) * AndroidUtilities.dp(4)) / albumsCount;
        }

        for (int a = 0; a < albumsCount; a++) {
            LayoutParams layoutParams = (LayoutParams) albumViews[a].getLayoutParams();
            layoutParams.topMargin = AndroidUtilities.dp(4);
            layoutParams.leftMargin = (itemWidth + AndroidUtilities.dp(4)) * a;
            layoutParams.width = itemWidth;
            layoutParams.height = itemWidth;
            layoutParams.gravity = Gravity.LEFT | Gravity.TOP;
            albumViews[a].setLayoutParams(layoutParams);
        }

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(4) + itemWidth, MeasureSpec.EXACTLY));
    }
}
