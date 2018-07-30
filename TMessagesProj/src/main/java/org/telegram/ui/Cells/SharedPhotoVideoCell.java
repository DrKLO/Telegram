/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Build;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoViewer;

public class SharedPhotoVideoCell extends FrameLayout {

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

    private class PhotoVideoView extends FrameLayout {

        private BackupImageView imageView;
        private TextView videoTextView;
        private FrameLayout videoInfoContainer;
        private View selector;
        private CheckBox checkBox;
        private FrameLayout container;
        private AnimatorSet animator;

        public PhotoVideoView(Context context) {
            super(context);

            container = new FrameLayout(context);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            imageView = new BackupImageView(context);
            imageView.getImageReceiver().setNeedsQualityThumb(true);
            imageView.getImageReceiver().setShouldGenerateQualityThumb(true);
            container.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            videoInfoContainer = new FrameLayout(context);
            videoInfoContainer.setBackgroundResource(R.drawable.phototime);
            videoInfoContainer.setPadding(AndroidUtilities.dp(3), 0, AndroidUtilities.dp(3), 0);
            container.addView(videoInfoContainer, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 16, Gravity.BOTTOM | Gravity.LEFT));

            ImageView imageView1 = new ImageView(context);
            imageView1.setImageResource(R.drawable.ic_video);
            videoInfoContainer.addView(imageView1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            videoTextView = new TextView(context);
            videoTextView.setTextColor(0xffffffff);
            videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            videoInfoContainer.addView(videoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, -0.7f, 0, 0));

            selector = new View(context);
            selector.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            checkBox = new CheckBox(context, R.drawable.round_check2);
            checkBox.setVisibility(INVISIBLE);
            checkBox.setColor(Theme.getColor(Theme.key_checkbox), Theme.getColor(Theme.key_checkboxCheck));
            addView(checkBox, LayoutHelper.createFrame(22, 22, Gravity.RIGHT | Gravity.TOP, 0, 2, 2, 0));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (Build.VERSION.SDK_INT >= 21) {
                selector.drawableHotspotChanged(event.getX(), event.getY());
            }
            return super.onTouchEvent(event);
        }

        public void setChecked(final boolean checked, boolean animated) {
            if (checkBox.getVisibility() != VISIBLE) {
                checkBox.setVisibility(VISIBLE);
            }
            checkBox.setChecked(checked, animated);
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
            if (animated) {
                if (checked) {
                    setBackgroundColor(0xfff5f5f5);
                }
                animator = new AnimatorSet();
                animator.playTogether(ObjectAnimator.ofFloat(container, "scaleX", checked ? 0.85f : 1.0f),
                        ObjectAnimator.ofFloat(container, "scaleY", checked ? 0.85f : 1.0f));
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator != null && animator.equals(animation)) {
                            animator = null;
                            if (!checked) {
                                setBackgroundColor(0);
                            }
                        }
                    }

                    @Override
                    public void onAnimationCancel(Animator animation) {
                        if (animator != null && animator.equals(animation)) {
                            animator = null;
                        }
                    }
                });
                animator.start();
            } else {
                setBackgroundColor(checked ? 0xfff5f5f5 : 0);
                container.setScaleX(checked ? 0.85f : 1.0f);
                container.setScaleY(checked ? 0.85f : 1.0f);
            }
        }

        @Override
        public void clearAnimation() {
            super.clearAnimation();
            if (animator != null) {
                animator.cancel();
                animator = null;
            }
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

    public void updateCheckboxColor() {
        for (int a = 0; a < 6; a++) {
            photoVideoViews[a].checkBox.setColor(Theme.getColor(Theme.key_checkbox), Theme.getColor(Theme.key_checkboxCheck));
        }
    }

    public void setDelegate(SharedPhotoVideoCellDelegate delegate) {
        this.delegate = delegate;
    }

    public void setItemsCount(int count) {
        for (int a = 0; a < photoVideoViews.length; a++) {
            photoVideoViews[a].clearAnimation();
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
        photoVideoViews[a].setChecked(checked, animated);
    }

    public void setItem(int a, int index, MessageObject messageObject) {
        messageObjects[a] = messageObject;
        indeces[a] = index;

        if (messageObject != null) {
            photoVideoViews[a].setVisibility(VISIBLE);

            PhotoVideoView photoVideoView = photoVideoViews[a];
            photoVideoView.imageView.getImageReceiver().setParentMessageObject(messageObject);
            photoVideoView.imageView.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(messageObject), false);
            if (messageObject.isVideo()) {
                photoVideoView.videoInfoContainer.setVisibility(VISIBLE);
                int duration = 0;
                for (int b = 0; b < messageObject.getDocument().attributes.size(); b++) {
                    TLRPC.DocumentAttribute attribute = messageObject.getDocument().attributes.get(b);
                    if (attribute instanceof TLRPC.TL_documentAttributeVideo) {
                        duration = attribute.duration;
                        break;
                    }
                }
                int minutes = duration / 60;
                int seconds = duration - minutes * 60;
                photoVideoView.videoTextView.setText(String.format("%d:%02d", minutes, seconds));
                if (messageObject.getDocument().thumb != null) {
                    TLRPC.FileLocation location = messageObject.getDocument().thumb.location;
                    photoVideoView.imageView.setImage(null, null, null, ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, location, "b", null, 0);
                } else {
                    photoVideoView.imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                photoVideoView.videoInfoContainer.setVisibility(INVISIBLE);
                TLRPC.PhotoSize photoSize = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                photoVideoView.imageView.setImage(null, null, null, ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, photoSize.location, "b", null, 0);
            } else {
                photoVideoView.videoInfoContainer.setVisibility(INVISIBLE);
                photoVideoView.imageView.setImageResource(R.drawable.photo_placeholder_in);
            }
        } else {
            photoVideoViews[a].clearAnimation();
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
