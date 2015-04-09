/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2014.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MediaController;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.FrameLayoutFixed;

public class PhotoPickerAlbumsCell extends FrameLayoutFixed {

    public interface PhotoPickerAlbumsCellDelegate {
        void didSelectAlbum(MediaController.AlbumEntry albumEntry);
    }

    private AlbumView[] albumViews;
    private MediaController.AlbumEntry[] albumEntries;
    private int albumsCount;
    private PhotoPickerAlbumsCellDelegate delegate;

    private class AlbumView extends FrameLayoutFixed {

        private BackupImageView imageView;
        private TextView nameTextView;
        private TextView countTextView;
        private View selector;

        public AlbumView(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            addView(imageView);
            LayoutParams layoutParams = (LayoutParams) imageView.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = LayoutParams.MATCH_PARENT;
            imageView.setLayoutParams(layoutParams);

            LinearLayout linearLayout = new LinearLayout(context);
            linearLayout.setOrientation(LinearLayout.HORIZONTAL);
            linearLayout.setBackgroundColor(0x7f000000);
            addView(linearLayout);
            layoutParams = (LayoutParams) linearLayout.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = AndroidUtilities.dp(28);
            layoutParams.gravity = Gravity.BOTTOM;
            linearLayout.setLayoutParams(layoutParams);

            nameTextView = new TextView(context);
            nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            nameTextView.setTextColor(0xffffffff);
            nameTextView.setSingleLine(true);
            nameTextView.setEllipsize(TextUtils.TruncateAt.END);
            nameTextView.setMaxLines(1);
            nameTextView.setGravity(Gravity.CENTER_VERTICAL);
            linearLayout.addView(nameTextView);
            LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) nameTextView.getLayoutParams();
            layoutParams1.width = 0;
            layoutParams1.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.leftMargin = AndroidUtilities.dp(8);
            layoutParams1.weight = 1;
            nameTextView.setLayoutParams(layoutParams1);

            countTextView = new TextView(context);
            countTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            countTextView.setTextColor(0xffaaaaaa);
            countTextView.setSingleLine(true);
            countTextView.setEllipsize(TextUtils.TruncateAt.END);
            countTextView.setMaxLines(1);
            countTextView.setGravity(Gravity.CENTER_VERTICAL);
            linearLayout.addView(countTextView);
            layoutParams1 = (LinearLayout.LayoutParams) countTextView.getLayoutParams();
            layoutParams1.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = LinearLayout.LayoutParams.MATCH_PARENT;
            layoutParams1.leftMargin = AndroidUtilities.dp(4);
            layoutParams1.rightMargin = AndroidUtilities.dp(4);
            countTextView.setLayoutParams(layoutParams1);

            selector = new View(context);
            selector.setBackgroundResource(R.drawable.list_selector);
            addView(selector);
            layoutParams = (LayoutParams) selector.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = LayoutParams.MATCH_PARENT;
            selector.setLayoutParams(layoutParams);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (Build.VERSION.SDK_INT >= 21) {
                selector.drawableHotspotChanged(event.getX(), event.getY());
            }
            return super.onTouchEvent(event);
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
            albumViews[a].setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (delegate != null) {
                        delegate.didSelectAlbum(albumEntries[(Integer) v.getTag()]);
                    }
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
                albumView.imageView.setOrientation(albumEntry.coverPhoto.orientation, true);
                albumView.imageView.setImage("thumb://" + albumEntry.coverPhoto.imageId + ":" + albumEntry.coverPhoto.path, null, getContext().getResources().getDrawable(R.drawable.nophotos));
            } else {
                albumView.imageView.setImageResource(R.drawable.nophotos);
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
            itemWidth = (AndroidUtilities.dp(490) - ((albumsCount + 1) * AndroidUtilities.dp(4))) / albumsCount;
        } else {
            itemWidth = (AndroidUtilities.displaySize.x - ((albumsCount + 1) * AndroidUtilities.dp(4))) / albumsCount;
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
