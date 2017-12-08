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
import android.util.TypedValue;
import android.view.Gravity;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;

public class PhotoPickerPhotoCell extends FrameLayout {

    public BackupImageView photoImage;
    public FrameLayout checkFrame;
    public CheckBox checkBox;
    public TextView videoTextView;
    public FrameLayout videoInfoContainer;
    private AnimatorSet animator;
    private AnimatorSet animatorSet;
    public int itemWidth;
    private boolean zoomOnSelect;

    public PhotoPickerPhotoCell(Context context, boolean zoom) {
        super(context);

        zoomOnSelect = zoom;

        photoImage = new BackupImageView(context);
        addView(photoImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

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
        videoInfoContainer.addView(videoTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 18, -0.7f, 0, 0));

        checkBox = new CheckBox(context, R.drawable.checkbig);
        checkBox.setSize(zoom ? 30 : 26);
        checkBox.setCheckOffset(AndroidUtilities.dp(1));
        checkBox.setDrawBackground(true);
        checkBox.setColor(0xff66bffa, 0xffffffff);
        addView(checkBox, LayoutHelper.createFrame(zoom ? 30 : 26, zoom ? 30 : 26, Gravity.RIGHT | Gravity.TOP, 0, 4, 4, 0));
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

    public void setChecked(final int num, final boolean checked, final boolean animated) {
        checkBox.setChecked(num, checked, animated);
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (zoomOnSelect) {
            if (animated) {
                if (checked) {
                    setBackgroundColor(0xff0a0a0a);
                }
                animator = new AnimatorSet();
                animator.playTogether(ObjectAnimator.ofFloat(photoImage, "scaleX", checked ? 0.85f : 1.0f),
                        ObjectAnimator.ofFloat(photoImage, "scaleY", checked ? 0.85f : 1.0f));
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
                setBackgroundColor(checked ? 0xff0A0A0A : 0);
                photoImage.setScaleX(checked ? 0.85f : 1.0f);
                photoImage.setScaleY(checked ? 0.85f : 1.0f);
            }
        }
    }
}
