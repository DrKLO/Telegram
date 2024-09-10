/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.os.Build;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DownloadController;
import org.telegram.messenger.ImageLocation;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox2;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoViewer;

public class SharedPhotoVideoCell extends FrameLayout {

    private PhotoVideoView[] photoVideoViews;
    private MessageObject[] messageObjects;
    private int[] indeces;
    private SharedPhotoVideoCellDelegate delegate;
    private int itemsCount;
    private boolean isFirst;
    private boolean ignoreLayout;
    private Paint backgroundPaint = new Paint();

    public final static int VIEW_TYPE_DEFAULT = 0;
    public final static int VIEW_TYPE_GLOBAL_SEARCH = 1;

    private int type;

    private int currentAccount = UserConfig.selectedAccount;

    public SharedPhotoVideoCellDelegate getDelegate() {
        return delegate;
    }

    public interface SharedPhotoVideoCellDelegate {
        void didClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a);

        boolean didLongClickItem(SharedPhotoVideoCell cell, int index, MessageObject messageObject, int a);
    }

    public class PhotoVideoView extends FrameLayout {

        private BackupImageView imageView;
        private TextView videoTextView;
        private FrameLayout videoInfoContainer;
        private View selector;
        private CheckBox2 checkBox;
        private FrameLayout container;
        private AnimatorSet animator;

        private MessageObject currentMessageObject;

        public PhotoVideoView(Context context) {
            super(context);

            setWillNotDraw(false);

            container = new FrameLayout(context);
            addView(container, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            imageView = new BackupImageView(context);
            imageView.getImageReceiver().setNeedsQualityThumb(true);
            imageView.getImageReceiver().setShouldGenerateQualityThumb(true);
            container.addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            videoInfoContainer = new FrameLayout(context) {

                private RectF rect = new RectF();

                @Override
                protected void onDraw(Canvas canvas) {
                    rect.set(0, 0, getMeasuredWidth(), getMeasuredHeight());
                    canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), Theme.chat_timeBackgroundPaint);
                }
            };
            videoInfoContainer.setWillNotDraw(false);
            videoInfoContainer.setPadding(AndroidUtilities.dp(5), 0, AndroidUtilities.dp(5), 0);
            container.addView(videoInfoContainer, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, 17, Gravity.BOTTOM | Gravity.LEFT, 4, 0, 0, 4));

            ImageView imageView1 = new ImageView(context);
            imageView1.setImageResource(R.drawable.play_mini_video);
            videoInfoContainer.addView(imageView1, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL));

            videoTextView = new TextView(context);
            videoTextView.setTextColor(0xffffffff);
            videoTextView.setTypeface(AndroidUtilities.bold());
            videoTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            videoTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            videoInfoContainer.addView(videoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 13, -0.7f, 0, 0));

            selector = new View(context);
            selector.setBackgroundDrawable(Theme.getSelectorDrawable(false));
            addView(selector, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

            checkBox = new CheckBox2(context, 21);
            checkBox.setVisibility(INVISIBLE);
            checkBox.setColor(-1, Theme.key_sharedMedia_photoPlaceholder, Theme.key_checkboxCheck);
            checkBox.setDrawUnchecked(false);
            checkBox.setDrawBackgroundAsArc(1);
            addView(checkBox, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.TOP, 0, 1, 1, 0));
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
                animator = new AnimatorSet();
                animator.playTogether(
                        ObjectAnimator.ofFloat(container, View.SCALE_X, checked ? 0.81f : 1.0f),
                        ObjectAnimator.ofFloat(container, View.SCALE_Y, checked ? 0.81f : 1.0f));
                animator.setDuration(200);
                animator.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (animator != null && animator.equals(animation)) {
                            animator = null;
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
                container.setScaleX(checked ? 0.85f : 1.0f);
                container.setScaleY(checked ? 0.85f : 1.0f);
            }
        }

        public void setMessageObject(MessageObject messageObject) {
            currentMessageObject = messageObject;
            imageView.getImageReceiver().setVisible(!PhotoViewer.isShowingImage(messageObject), false);
            String restrictionReason = MessagesController.getInstance(currentAccount).getRestrictionReason(messageObject.messageOwner.restriction_reason);
            if (!TextUtils.isEmpty(restrictionReason)) {
                videoInfoContainer.setVisibility(INVISIBLE);
                imageView.setImageResource(R.drawable.photo_placeholder_in);
            } else if (messageObject.isVideo()) {
                videoInfoContainer.setVisibility(VISIBLE);
                videoTextView.setText(AndroidUtilities.formatShortDuration((int) messageObject.getDuration()));
                TLRPC.Document document = messageObject.getDocument();
                TLRPC.PhotoSize thumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 50);
                TLRPC.PhotoSize qualityThumb = FileLoader.getClosestPhotoSizeWithSize(document.thumbs, 320);
                if (thumb == qualityThumb) {
                    qualityThumb = null;
                }
                if (thumb != null) {
                    if (messageObject.strippedThumb != null) {
                        imageView.setImage(ImageLocation.getForDocument(qualityThumb, document), "100_100", null, messageObject.strippedThumb, messageObject);
                    } else {
                        imageView.setImage(ImageLocation.getForDocument(qualityThumb, document), "100_100", ImageLocation.getForDocument(thumb, document), "b", ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, null, 0, messageObject);
                    }
                } else {
                    imageView.setImageResource(R.drawable.photo_placeholder_in);
                }
            } else if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaPhoto && messageObject.messageOwner.media.photo != null && !messageObject.photoThumbs.isEmpty()) {
                videoInfoContainer.setVisibility(INVISIBLE);
                TLRPC.PhotoSize currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 50);
                TLRPC.PhotoSize currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 320, false, currentPhotoObjectThumb, false);
                if (messageObject.mediaExists || DownloadController.getInstance(currentAccount).canDownloadMedia(messageObject)) {
                    if (currentPhotoObject == currentPhotoObjectThumb) {
                        currentPhotoObjectThumb = null;
                    }
                    if (messageObject.strippedThumb != null) {
                        imageView.getImageReceiver().setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "100_100", null, null, messageObject.strippedThumb, currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                    } else {
                        imageView.getImageReceiver().setImage(ImageLocation.getForObject(currentPhotoObject, messageObject.photoThumbsObject), "100_100", ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", currentPhotoObject != null ? currentPhotoObject.size : 0, null, messageObject, messageObject.shouldEncryptPhotoOrVideo() ? 2 : 1);
                    }
                } else {
                    if (messageObject.strippedThumb != null) {
                        imageView.setImage(null, null, null, null, messageObject.strippedThumb, null, null, 0, messageObject);
                    } else {
                        imageView.setImage(null, null, ImageLocation.getForObject(currentPhotoObjectThumb, messageObject.photoThumbsObject), "b", ApplicationLoader.applicationContext.getResources().getDrawable(R.drawable.photo_placeholder_in), null, null, 0, messageObject);
                    }
                }
            } else {
                videoInfoContainer.setVisibility(INVISIBLE);
                imageView.setImageResource(R.drawable.photo_placeholder_in);
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

        @Override
        protected void onDraw(Canvas canvas) {
            if (checkBox.isChecked() || !imageView.getImageReceiver().hasBitmapImage() || imageView.getImageReceiver().getCurrentAlpha() != 1.0f || PhotoViewer.isShowingImage(currentMessageObject)) {
                canvas.drawRect(0, 0, getMeasuredWidth(), getMeasuredHeight(), backgroundPaint);
            }
        }

        @Override
        public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(info);
            if (currentMessageObject.isVideo()) {
                info.setText(LocaleController.getString(R.string.AttachVideo) + ", " + LocaleController.formatDuration((int) currentMessageObject.getDuration()));
            } else {
                info.setText(LocaleController.getString(R.string.AttachPhoto));
            }
            if (checkBox.isChecked()) {
                info.setCheckable(true);
                info.setChecked(true);
            }
        }
    }

    public SharedPhotoVideoCell(Context context) {
        this(context, VIEW_TYPE_DEFAULT);
    }
    public SharedPhotoVideoCell(Context context, int type) {
        super(context);
        this.type = type;

        backgroundPaint.setColor(Theme.getColor(Theme.key_sharedMedia_photoPlaceholder));
        messageObjects = new MessageObject[6];
        photoVideoViews = new PhotoVideoView[6];
        indeces = new int[6];
        for (int a = 0; a < 6; a++) {
            photoVideoViews[a] = new PhotoVideoView(context);
            addView(photoVideoViews[a]);
            photoVideoViews[a].setVisibility(INVISIBLE);
            photoVideoViews[a].setTag(a);
            photoVideoViews[a].setOnClickListener(v -> {
                if (delegate != null) {
                    int a1 = (Integer) v.getTag();
                    delegate.didClickItem(SharedPhotoVideoCell.this, indeces[a1], messageObjects[a1], a1);
                }
            });
            photoVideoViews[a].setOnLongClickListener(v -> {
                if (delegate != null) {
                    int a12 = (Integer) v.getTag();
                    return delegate.didLongClickItem(SharedPhotoVideoCell.this, indeces[a12], messageObjects[a12], a12);
                }
                return false;
            });
        }
    }

    public void updateCheckboxColor() {
        for (int a = 0; a < 6; a++) {
            photoVideoViews[a].checkBox.invalidate();
        }
    }

    @Override
    public void invalidate() {
        for (int a = 0; a < 6; a++) {
            photoVideoViews[a].invalidate();
        }
        super.invalidate();
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
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

    public PhotoVideoView getView(int a) {
        if (a >= itemsCount) {
            return null;
        }
        return photoVideoViews[a];
    }

    public MessageObject getMessageObject(int a) {
        if (a >= itemsCount) {
            return null;
        }
        return messageObjects[a];
    }

    public int getIndeces(int a) {
        if (a >= itemsCount) {
            return -1;
        }
        return indeces[a];
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
            photoVideoViews[a].setMessageObject(messageObject);
        } else {
            photoVideoViews[a].clearAnimation();
            photoVideoViews[a].setVisibility(INVISIBLE);
            messageObjects[a] = null;
        }
    }

    @Override
    public void requestLayout() {
        if (ignoreLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int itemWidth;
        if (type == VIEW_TYPE_GLOBAL_SEARCH) {
            itemWidth = (MeasureSpec.getSize(widthMeasureSpec) - (itemsCount - 1) * AndroidUtilities.dp(2)) / itemsCount;
        } else {
            itemWidth = getItemSize(itemsCount);
        }

        ignoreLayout = true;
        for (int a = 0; a < itemsCount; a++) {
            LayoutParams layoutParams = (LayoutParams) photoVideoViews[a].getLayoutParams();
            layoutParams.topMargin = isFirst ? 0 : AndroidUtilities.dp(2);
            layoutParams.leftMargin = (itemWidth + AndroidUtilities.dp(2)) * a;
            if (a == itemsCount - 1) {
                if (AndroidUtilities.isTablet()) {
                    layoutParams.width = AndroidUtilities.dp(490) - (itemsCount - 1) * (AndroidUtilities.dp(2) + itemWidth);
                } else {
                    layoutParams.width = AndroidUtilities.displaySize.x - (itemsCount - 1) * (AndroidUtilities.dp(2) + itemWidth);
                }
            } else {
                layoutParams.width = itemWidth;
            }
            layoutParams.height = itemWidth;
            layoutParams.gravity = Gravity.TOP | Gravity.LEFT;
            photoVideoViews[a].setLayoutParams(layoutParams);
        }
        ignoreLayout = false;

        super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec((isFirst ? 0 : AndroidUtilities.dp(2)) + itemWidth, MeasureSpec.EXACTLY));
    }

    public static int getItemSize(int itemsCount) {
        final int itemWidth;
        if (AndroidUtilities.isTablet()) {
            itemWidth = (AndroidUtilities.dp(490) - (itemsCount - 1) * AndroidUtilities.dp(2)) / itemsCount;
        } else {
            itemWidth = (AndroidUtilities.displaySize.x - (itemsCount - 1) * AndroidUtilities.dp(2)) / itemsCount;
        }
        return itemWidth;
    }
}
