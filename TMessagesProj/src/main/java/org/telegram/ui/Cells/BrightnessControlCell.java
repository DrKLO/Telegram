/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.os.Bundle;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.FrameLayout;
import android.widget.ImageView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

public class BrightnessControlCell extends FrameLayout {

    private ImageView leftImageView;
    private ImageView rightImageView;
    private SeekBarView seekBarView;

    public BrightnessControlCell(Context context) {
        super(context);

        leftImageView = new ImageView(context);
        leftImageView.setImageResource(R.drawable.brightness_low);
        addView(leftImageView, LayoutHelper.createFrame(24, 24, Gravity.LEFT | Gravity.TOP, 17, 12, 0, 0));

        seekBarView = new SeekBarView(context, /* inPercents = */ true) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onTouchEvent(event);
            }
        };
        seekBarView.setReportChanges(true);
        seekBarView.setDelegate(new SeekBarView.SeekBarViewDelegate() {
            @Override
            public void onSeekBarDrag(boolean stop, float progress) {
                didChangedValue(progress);
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }

            @Override
            public CharSequence getContentDescription() {
                return " ";
            }
        });
        seekBarView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 54, 5, 54, 0));

        rightImageView = new ImageView(context);
        rightImageView.setImageResource(R.drawable.brightness_high);
        addView(rightImageView, LayoutHelper.createFrame(24, 24, Gravity.RIGHT | Gravity.TOP, 0, 12, 17, 0));
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        leftImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
        rightImageView.setColorFilter(new PorterDuffColorFilter(Theme.getColor(Theme.key_windowBackgroundWhiteGrayIcon), PorterDuff.Mode.MULTIPLY));
    }

    protected void didChangedValue(float value) {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(48), MeasureSpec.EXACTLY));
    }

    public void setProgress(float value) {
        seekBarView.setProgress(value);
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        seekBarView.getSeekBarAccessibilityDelegate().onInitializeAccessibilityNodeInfoInternal(this, info);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle arguments) {
        return super.performAccessibilityAction(action, arguments) || seekBarView.getSeekBarAccessibilityDelegate().performAccessibilityActionInternal(this, action, arguments);
    }
}
