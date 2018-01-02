/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.SimpleTextView;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

public class MaxFileSizeCell extends FrameLayout {

    private TextView textView;
    private SimpleTextView sizeTextView;
    private SeekBarView seekBarView;

    private long maxSize;

    public MaxFileSizeCell(Context context) {
        super(context);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setText(LocaleController.getString("AutodownloadSizeLimit", R.string.AutodownloadSizeLimit));
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 64 : 17, 13, LocaleController.isRTL ? 17 : 64, 0));

        sizeTextView = new SimpleTextView(context);
        sizeTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlueText6));
        sizeTextView.setTextSize(16);
        sizeTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        addView(sizeTextView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 17 : 64, 13, LocaleController.isRTL ? 64 : 17, 0));

        seekBarView = new SeekBarView(context) {
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
            public void onSeekBarDrag(float progress) {
                int size;
                if (maxSize > 1024 * 1024 * 10) {
                    int min = 1024 * 1024 * 100;
                    if (progress <= 0.8f) {
                        size = (int) (min * (progress / 0.8f));
                    } else {
                        size = (int) (min + (maxSize - min) * (progress - 0.8f) / 0.2f);
                    }
                } else {
                    size = (int) (maxSize * progress);
                }
                sizeTextView.setText(LocaleController.formatString("AutodownloadSizeLimitUpTo", R.string.AutodownloadSizeLimitUpTo, AndroidUtilities.formatFileSize(size)));
                didChangedSizeValue(size);
            }
        });
        addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 30, Gravity.TOP | Gravity.LEFT, 4, 40, 4, 0));
    }

    protected void didChangedSizeValue(int value) {

    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));
    }

    public void setSize(long size, long max) {
        maxSize = max;
        float progress;
        if (maxSize > 1024 * 1024 * 10) {
            int min = 1024 * 1024 * 100;
            if (size <= min) {
                progress = size / (float) min * 0.8f;
            } else {
                progress = 0.8f + (size - min) / (float) (maxSize - min) * 0.2f;
            }
        } else {
            progress = size / (float) maxSize;
        }
        seekBarView.setProgress(progress);
        sizeTextView.setText(LocaleController.formatString("AutodownloadSizeLimitUpTo", R.string.AutodownloadSizeLimitUpTo, AndroidUtilities.formatFileSize(size)));
    }
}
