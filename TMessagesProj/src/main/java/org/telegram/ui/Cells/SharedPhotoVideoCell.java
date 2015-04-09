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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.android.AndroidUtilities;
import org.telegram.android.MessageObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.TLRPC;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.FrameLayoutFixed;
import org.telegram.ui.PhotoViewer;

public class SharedPhotoVideoCell extends FrameLayoutFixed {

    private PhotoVideoView[] photoVideoViews;
    private MessageObject[] messageObjects;
    private int[] indeces;
    private SharedPhotoVideoCellDelegate delegate;
    private int itemsCount;
    private boolean isFirst;

    public interface SharedPhotoVideoCellDelegate {
        void didClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a);
        boolean didLongClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a);
    }

    private class PhotoVideoView extends FrameLayoutFixed {

        private BackupImageView imageView;
        private TextView videoTextView;
        private LinearLayout videoInfoContainer;
        private View selector;
        private CheckBox checkBox;

        public PhotoVideoView(Context context) {
            super(context);

            imageView = new BackupImageView(context);
            imageView.getImageReceiver().setNeedsQualityThumb(true);
            imageView.getImageReceiver().setShouldGenerateQualityThumb(true);
            addView(imageView);
            LayoutParams layoutParams = (LayoutParams) imageView.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = LayoutParams.MATCH_PARENT;
            imageView.setLayoutParams(layoutParams);

            videoInfoContainer = new LinearLayout(context);
            videoInfoContainer.setOrientation(LinearLayout.HORIZONTAL);
            videoInfoContainer.setBackgroundResource(R.drawable.phototime);
            videoInfoContainer.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
            videoInfoContainer.setGravity(Gravity.CENTER_VERTICAL);
            addView(videoInfoContainer);
            layoutParams = (LayoutParams) videoInfoContainer.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = AndroidUtilities.dp(16);
            layoutParams.gravity = Gravity.BOTTOM | Gravity.LEFT;
            videoInfoContainer.setLayoutParams(layoutParams);

            ImageView imageView1 = new ImageView(context);
            imageView1.setImageResource(R.drawable.ic_video);
            videoInfoContainer.addView(imageView1);
            LinearLayout.LayoutParams layoutParams1 = (LinearLayout.LayoutParams) imageView1.getLayoutParams();
            layoutParams1.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            imageView1.setLayoutParams(layoutParams1);

            videoTextView = new TextView(context);
            videoTextView.setTextColor(0xffffffff);
            videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            videoTextView.setGravity(Gravity.CENTER_VERTICAL);
            videoInfoContainer.addView(videoTextView);
            layoutParams1 = (LinearLayout.LayoutParams) videoTextView.getLayoutParams();
            layoutParams1.width = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.height = LinearLayout.LayoutParams.WRAP_CONTENT;
            layoutParams1.leftMargin = AndroidUtilities.dp(4);
            layoutParams1.gravity = Gravity.CENTER_VERTICAL;
            layoutParams1.bottomMargin = AndroidUtilities.dp(1);
            videoTextView.setLayoutParams(layoutParams1);

            selector = new View(context);
            selector.setBackgroundResource(R.drawable.list_selector);
            addView(selector);
            layoutParams = (LayoutParams) selector.getLayoutParams();
            layoutParams.width = LayoutParams.MATCH_PARENT;
            layoutParams.height = LayoutParams.MATCH_PARENT;
            selector.setLayoutParams(layoutParams);

            checkBox = new CheckBox(context, R.drawable.round_check2);
            checkBox.setVisibility(INVISIBLE);
            addView(checkBox);
            layoutParams = (LayoutParams) checkBox.getLayoutParams();
            layoutParams.width = AndroidUtilities.dp(22);
            layoutParams.height = AndroidUtilities.dp(22);
            layoutParams.gravity = Gravity.RIGHT | Gravity.TOP;
            layoutParams.topMargin = AndroidUtilities.dp(6);
            layoutParams.rightMargin = AndroidUtilities.dp(6);
            checkBox.setLayoutParams(layoutParams);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (Build.VERSION.SDK_INT >= 21) {
                selector.drawableHotspotChanged(event.getX(), event.getY());
            }
            return super.onTouchEvent(event);
        }
    }

    public SharedPhotoVideoCell(Context context) {
        super(context);

        messageObjects = new MessageObject[6];
        photoVideoViews = new PhotoVideoView[6];
        indeces = new int[6];
        for (int a = 0; a < 6; a++) {
            photoVideoViews[a] = new PhotoVideoView(context);
            addView(photoVideoViews[a]);
            photoVideoViews[a].setVisibility(INVISIBLE);
            photoVideoViews[a].setTag(a);
            photoVideoViews[a].setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (delegate != null) {
                        int a = (Integer) v.getTag();
                        delegate.didClickItem(SharedPhotoVideoCell.this, indeces[a], messageObjects[a], a);
                    }
                }
            });
            photoVideoViews[a].setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View v) {
                    if (delegate != null) {
                        int a = (Integer) v.getTag();
                        return delegate.didLongClickItem(SharedPhotoVideoCell.this, indeces[a], messageObjects[a], a);
                    }
                    return false;
                }
            });
        }
    }

    public void setDelegate(SharedPhotoVideoCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setItemsCount(int count) {
        for (int a = 0; a < photoVideoViews.length; a++) {
            photoVideoViews[a].setVisibility(a < count ? VISIBLE : INVISIBLE);
        }
        itemsCount = count;
    }

    public BackupImageView getImageView(int a) {
        if (a >= itemsCount) {
            return null;
        }
        return photoVideoViews[a].imageView;
    }

    public MessageObject getMessageObject(int a) {
        if (a >= itemsCount) {
            return null;
        }
        return messageObjects[a];
    }

    public void setIsFirst(boolean first) {
        isFirst = first;
    }

    public void setChecked(int a, boolean checked, boolean animated) {
        if (photoVideoViews[a].checkBox.getVisibility() != VISIBLE) {
            photoVideoViews[a].checkBox.setVisibility(VISIBLE);
        }
        photoVideoViews[a].checkBox.setChecked(checked, animated);
    }

    public void setItem(int a, int index, MessageObject messageObject) {
        messageObjects[a] = messageObject;
        indeces[a] = index;

        if (messageObject != null) {
            photoVideoViews[a].setVisibility(VISIBLE);

            PhotoVideoView photoVideoView = photoVideoViews[a];
            photoVideoView.imageView.getImageReceiver().setParentMessageObject(messageObject);
            photoVideoView.imageView.getImageReceiver().setVisible(!PhotoViewer.getInstance().isShowingImage(messageObject), false);
            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaVideo && messageObject.messageOwner.media.video != null) {
                photoVideoView.videoInfoContainer.setVisibility(VISIBLE);
                int duration = messageObject.messageOwner.media.video.duration;
                int minutes = duration / 60;
                int seconds = duration - minutes * 60;
                photoVideoView.videoTextView.setText(String.format("%d:%02d", minutes, seconds));
                if (messageObject.messageOwner.media.video.thumb != null) {
                    TLRPC.FileLocation location = messageObject.messageOwner.media.video.thumb.location;
                    photoVideoView.imageView.setImage(null, null, null, ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, location, "b", 0);
                } else {
                    photoVideoView.imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                photoVideoView.videoInfoContainer.setVisibility(INVISIBLE);
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                photoVideoView.imageView.setImage(null, null, null, ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, photoSize.location, "b", 0);
            } else {
                photoVideoView.videoInfoContainer.setVisibility(INVISIBLE);
                photoVideoView.imageView.setImageResource(R.drawable.photo_placeholder_in);
            }
        } else {
            photoVideoViews[a].setVisibility(INVISIBLE);
            messageObjects[a] = null;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int itemWidth;
        if (AndroidUtilities.isTablet()) {
            itemWidth = (AndroidUtilities.dp(490) - ((itemsCount + 1) * AndroidUtilities.dp(4))) / itemsCount;
        } else {
            itemWidth = (AndroidUtilities.displaySize.x - ((itemsCount + 1) * AndroidUtilities.dp(4))) / itemsCount;
        }

        for (int a = 0; a < itemsCount; a++) {
            LayoutParams layoutParams = (LayoutParams) photoVideoViews[a].getLayoutParams();
            layoutParams.topMargin = isFirst ? 0 : AndroidUtilities.dp(4);
            layoutParams.leftMargin = (itemWidth + AndroidUtilities.dp(4)) * a + AndroidUtilities.dp(4);
            layoutParams.width = itemWidth;
            layoutParams.height = itemWidth;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            photoVideoViews[a].setLayoutParams(layoutParams);
        }

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((isFirst ? 0 : AndroidUtilities.dp(4)) + itemWidth, MeasureSpec.EXACTLY));
    }
}
