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
import android.graphics.drawable.Drawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
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
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.PhotoViewer;

public class PhotoPickerPhotoCell extends FrameLayout {

    public BackupImageView imageView;
    public FrameLayout checkFrame;
    public CheckBox checkBox;
    public TextView videoTextView;
    public FrameLayout videoInfoContainer;
    private AnimatorSet animator;
    private AnimatorSet animatorSet;
    public int itemWidth;
    private boolean zoomOnSelect;
    private Paint backgroundPaint = new Paint();
    private MediaController.PhotoEntry photoEntry;

    public PhotoPickerPhotoCell(Context context, boolean zoom) {
        super(context);
        setWillNotDraw(false);

        zoomOnSelect = zoom;

        imageView = new BackupImageView(context);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        checkFrame = new FrameLayout(context);
        addView(checkFrame, LayoutHelper.createFrame(42, 42, Gravity.RIGHT | Gravity.TOP));

        videoInfoContainer = new FrameLayout(context);
        videoInfoContainer.setBackgroundResource(R.drawable.phototime);
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

        checkBox = new CheckBox(context, R.drawable.checkbig);
        checkBox.setSize(zoom ? 30 : 26);
        checkBox.setCheckOffset(AndroidUtilities.dp(1));
        checkBox.setDrawBackground(true);
        checkBox.setColor(0xff66bffa, 0xffffffff);
        addView(checkBox, LayoutHelper.createFrame(zoom ? 30 : 26, zoom ? 30 : 26, Gravity.RIGHT | Gravity.TOP, 0, 4, 4, 0));

        setFocusable(true);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY));
    }

    public void showCheck(boolean show) {
        if (animatorSet != null) {
            animatorSet.cancel();
            animatorSet = null;
        }
        animatorSet = new AnimatorSet();
        animatorSet.setInterpolator(new DecelerateInterpolator());
        animatorSet.setDuration(180);
        animatorSet.playTogether(
                ObjectAnimator.ofFloat(videoInfoContainer, "alpha", show ? 1.0f : 0.0f),
                ObjectAnimator.ofFloat(checkBox, "alpha", show ? 1.0f : 0.0f));
        animatorSet.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (animation.equals(animatorSet)) {
                    animatorSet = null;
                }
            }
        });
        animatorSet.start();
    }

    public void setNum(int num) {
        checkBox.setNum(num);
    }

    public void setImage(MediaController.PhotoEntry entry) {
        Drawable thumb = zoomOnSelect ? Theme.chat_attachEmptyDrawable : getResources().getDrawable(R.drawable.nophotos);
        photoEntry = entry;
        if (photoEntry.thumbPath != null) {
            imageView.setImage(photoEntry.thumbPath, null, thumb);
        } else if (photoEntry.path != null) {
            imageView.setOrientation(photoEntry.orientation, true);
            if (photoEntry.isVideo) {
                videoInfoContainer.setVisibility(View.VISIBLE);
                int minutes = photoEntry.duration / 60;
                int seconds = photoEntry.duration - minutes * 60;
                videoTextView.setText(String.format("%d:%02d", minutes, seconds));
                setContentDescription(LocaleController.getString("AttachVideo", R.string.AttachVideo) + ", " + LocaleController.formatCallDuration(photoEntry.duration));
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
        Drawable thumb = zoomOnSelect ? Theme.chat_attachEmptyDrawable : getResources().getDrawable(R.drawable.nophotos);
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
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (zoomOnSelect) {
            if (animated) {
                animator = new AnimatorSet();
                animator.playTogether(ObjectAnimator.ofFloat(imageView, View.SCALE_X, checked ? 0.85f : 1.0f),
                        ObjectAnimator.ofFloat(imageView, View.SCALE_Y, checked ? 0.85f : 1.0f));
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
                imageView.setScaleX(checked ? 0.85f : 1.0f);
                imageView.setScaleY(checked ? 0.85f : 1.0f);
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!zoomOnSelect) {
            return;
        }
        if (checkBox.isChecked() || imageView.getScaleX() != 1.0f || !imageView.getImageReceiver().hasNotThumb() || imageView.getImageReceiver().getCurrentAlpha() != 1.0f || photoEntry != null && PhotoViewer.isShowingImage(photoEntry.path)) {
            backgroundPaint.setColor(Theme.getColor(Theme.key_chat_attachPhotoBackground));
            canvas.drawRect(0, 0, imageView.getMeasuredWidth(), imageView.getMeasuredHeight(), backgroundPaint);
        }
    }
}
