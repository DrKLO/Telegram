/*
 * This is the source code of Telegram for Android v. 2.0.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.view.Gravity;
import android.widget.FrameLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.AnimationCompat.AnimatorListenerAdapterProxy;
import org.telegram.messenger.AnimationCompat.AnimatorSetProxy;
import org.telegram.messenger.AnimationCompat.ObjectAnimatorProxy;
import org.telegram.messenger.AnimationCompat.ViewProxy;
import org.telegram.messenger.R;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.CheckBox;
import org.telegram.ui.Components.LayoutHelper;

public class PhotoPickerPhotoCell extends FrameLayout {

    public BackupImageView photoImage;
    public FrameLayout checkFrame;
    public CheckBox checkBox;
    private AnimatorSetProxy animator;
    public int itemWidth;

    public PhotoPickerPhotoCell(Context context) {
        super(context);

        photoImage = new BackupImageView(context);
        addView(photoImage, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        checkFrame = new FrameLayout(context);
        addView(checkFrame, LayoutHelper.createFrame(42, 42, Gravity.RIGHT | Gravity.TOP));

        checkBox = new CheckBox(context, R.drawable.checkbig);
        checkBox.setSize(30);
        checkBox.setCheckOffset(AndroidUtilities.dp(1));
        checkBox.setDrawBackground(true);
        checkBox.setColor(0xff3ccaef);
        addView(checkBox, LayoutHelper.createFrame(30, 30, Gravity.RIGHT | Gravity.TOP, 0, 4, 4, 0));
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(itemWidth, MeasureSpec.EXACTLY));
    }

    public void setChecked(final boolean checked, final boolean animated) {
        checkBox.setChecked(checked, animated);
        if (animator != null) {
            animator.cancel();
            animator = null;
        }
        if (animated) {
            if (checked) {
                setBackgroundColor(0xff0A0A0A);
            }
            animator = new AnimatorSetProxy();
            animator.playTogether(ObjectAnimatorProxy.ofFloat(photoImage, "scaleX", checked ? 0.85f : 1.0f),
                    ObjectAnimatorProxy.ofFloat(photoImage, "scaleY", checked ? 0.85f : 1.0f));
            animator.setDuration(200);
            animator.addListener(new AnimatorListenerAdapterProxy() {
                @Override
                public void onAnimationEnd(Object animation) {
                    if (animator != null && animator.equals(animation)) {
                        animator = null;
                        if (!checked) {
                            setBackgroundColor(0);
                        }
                    }
                }

                @Override
                public void onAnimationCancel(Object animation) {
                    if (animator != null && animator.equals(animation)) {
                        animator = null;
                    }
                }
            });
            animator.start();
        } else {
            setBackgroundColor(checked ? 0xff0A0A0A : 0);
            ViewProxy.setScaleX(photoImage, checked ? 0.85f : 1.0f);
            ViewProxy.setScaleY(photoImage, checked ? 0.85f : 1.0f);
        }
    }
}
