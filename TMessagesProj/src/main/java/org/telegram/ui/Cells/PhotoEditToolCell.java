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
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.PhotoEditorSeekBar;

public class PhotoEditToolCell extends FrameLayout {

    private TextView nameTextView;
    private TextView valueTextView;
    private PhotoEditorSeekBar seekBar;
    private AnimatorSet valueAnimation;
    private Runnable hideValueRunnable = new Runnable() {
        @Override
        public void run() {
            valueTextView.setTag(null);
            valueAnimation = new AnimatorSet();
            valueAnimation.playTogether(
                    ObjectAnimator.ofFloat(valueTextView, View.ALPHA, 0.0f),
                    ObjectAnimator.ofFloat(nameTextView, View.ALPHA, 1.0f));
            valueAnimation.setDuration(250);
            valueAnimation.setInterpolator(new DecelerateInterpolator());
            valueAnimation.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    if (animation.equals(valueAnimation)) {
                        valueAnimation = null;
                    }
                }
            });
            valueAnimation.start();
        }
    };

    public PhotoEditToolCell(Context context) {
        super(context);

        nameTextView = new TextView(context);
        nameTextView.setGravity(Gravity.RIGHT);
        nameTextView.setTextColor(0xffffffff);
        nameTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        nameTextView.setMaxLines(1);
        nameTextView.setSingleLine(true);
        nameTextView.setEllipsize(TextUtils.TruncateAt.END);
        addView(nameTextView, LayoutHelper.createFrame(80, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        valueTextView = new TextView(context);
        valueTextView.setTextColor(0xff6cc3ff);
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
        valueTextView.setGravity(Gravity.RIGHT);
        valueTextView.setSingleLine(true);
        addView(valueTextView, LayoutHelper.createFrame(80, LayoutHelper.WRAP_CONTENT, Gravity.LEFT | Gravity.CENTER_VERTICAL, 0, 0, 0, 0));

        seekBar = new PhotoEditorSeekBar(context);
        addView(seekBar, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 40, Gravity.LEFT | Gravity.CENTER_VERTICAL, 96, 0, 24, 0));
    }

    public void setSeekBarDelegate(final PhotoEditorSeekBar.PhotoEditorSeekBarDelegate photoEditorSeekBarDelegate) {
        seekBar.setDelegate((i, progress) -> {
            photoEditorSeekBarDelegate.onProgressChanged(i, progress);
            if (progress > 0) {
                valueTextView.setText("+" + progress);
            } else {
                valueTextView.setText("" + progress);
            }
            if (valueTextView.getTag() == null) {
                if (valueAnimation != null) {
                    valueAnimation.cancel();
                }
                valueTextView.setTag(1);
                valueAnimation = new AnimatorSet();
                valueAnimation.playTogether(
                        ObjectAnimator.ofFloat(valueTextView, View.ALPHA, 1.0f),
                        ObjectAnimator.ofFloat(nameTextView, View.ALPHA, 0.0f));
                valueAnimation.setDuration(250);
                valueAnimation.setInterpolator(new DecelerateInterpolator());
                valueAnimation.addListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        AndroidUtilities.runOnUIThread(hideValueRunnable, 1000);
                    }
                });
                valueAnimation.start();
            } else {
                AndroidUtilities.cancelRunOnUIThread(hideValueRunnable);
                AndroidUtilities.runOnUIThread(hideValueRunnable, 1000);
            }
        });
    }

    @Override
    public void setTag(Object tag) {
        super.setTag(tag);
        seekBar.setTag(tag);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(40), MeasureSpec.EXACTLY));
    }

    public void setIconAndTextAndValue(String text, float value, int min, int max) {
        if (valueAnimation != null) {
            valueAnimation.cancel();
            valueAnimation = null;
        }
        AndroidUtilities.cancelRunOnUIThread(hideValueRunnable);
        valueTextView.setTag(null);
        nameTextView.setText(text.substring(0, 1).toUpperCase() + text.substring(1).toLowerCase());
        if (value > 0) {
            valueTextView.setText("+" + (int) value);
        } else {
            valueTextView.setText("" + (int) value);
        }
        valueTextView.setAlpha(0.0f);
        nameTextView.setAlpha(1.0f);
        seekBar.setMinMax(min, max);
        seekBar.setProgress((int) value, false);
    }
}
