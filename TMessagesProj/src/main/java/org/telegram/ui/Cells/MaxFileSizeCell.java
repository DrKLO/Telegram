/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.animation.Animator;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.SeekBarView;

import java.util.ArrayList;

public class MaxFileSizeCell extends FrameLayout {

    private TextView textView;
    private TextView sizeTextView;
    private SeekBarView seekBarView;

    private long currentSize;

    public MaxFileSizeCell(Context context) {
        super(context);

        setWillNotDraw(false);

        textView = new TextView(context);
        textView.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        textView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        textView.setLines(1);
        textView.setMaxLines(1);
        textView.setSingleLine(true);
        textView.setGravity((LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP);
        textView.setEllipsize(TextUtils.TruncateAt.END);
        textView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(textView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, 21, 13, 21, 0));

        sizeTextView = new TextView(context);
        sizeTextView.setTextColor(Theme.getColor(Theme.key_dialogTextBlue2));
        sizeTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        sizeTextView.setLines(1);
        sizeTextView.setMaxLines(1);
        sizeTextView.setSingleLine(true);
        sizeTextView.setGravity((LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP);
        sizeTextView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(sizeTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.MATCH_PARENT, (LocaleController.isRTL ? Gravity.LEFT : Gravity.RIGHT) | Gravity.TOP, 21, 13, 21, 0));

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
            public void onSeekBarDrag(boolean stop, float progress) {
                int size = 500 * 1024;
                if (progress <= 0.25f) {
                    size += 524 * 1024 * (progress / 0.25f);
                } else {
                    progress -= 0.25f;
                    size += 524 * 1024;

                    if (progress < 0.25f) {
                        size += 9 * 1024 * 1024 * (progress / 0.25f);
                    } else {
                        progress -= 0.25f;
                        size += 9 * 1024 * 1024;

                        if (progress <= 0.25f) {
                            size += 90 * 1024 * 1024 * (progress / 0.25f);
                        } else {
                            progress -= 0.25f;
                            size += 90 * 1024 * 1024;

                            size += (FileLoader.DEFAULT_MAX_FILE_SIZE - size) * (progress / 0.25f);
                        }
                    }
                }
                sizeTextView.setText(LocaleController.formatString("AutodownloadSizeLimitUpTo", R.string.AutodownloadSizeLimitUpTo, AndroidUtilities.formatFileSize(size)));
                currentSize = size;
                didChangedSizeValue(size);
            }

            @Override
            public void onSeekBarPressed(boolean pressed) {
            }

            @Override
            public CharSequence getContentDescription() {
                return textView.getText() + " " + sizeTextView.getText();
            }
        });
        seekBarView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        addView(seekBarView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, 38, Gravity.TOP | Gravity.LEFT, 6, 36, 6, 0));

        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
        setAccessibilityDelegate(seekBarView.getSeekBarAccessibilityDelegate());
    }

    protected void didChangedSizeValue(int value) {

    }

    public void setText(String text) {
        textView.setText(text);
    }

    public long getSize() {
        return currentSize;
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(80), MeasureSpec.EXACTLY));

        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(80));

        int availableWidth = getMeasuredWidth() - AndroidUtilities.dp(42);

        sizeTextView.measure(MeasureSpec.makeMeasureSpec(availableWidth, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY));
        int width = Math.max(AndroidUtilities.dp(10), availableWidth - sizeTextView.getMeasuredWidth() - AndroidUtilities.dp(8));

        textView.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY));

        seekBarView.measure(MeasureSpec.makeMeasureSpec(getMeasuredWidth() - AndroidUtilities.dp(20), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(30), MeasureSpec.EXACTLY));
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return true;
        }
        return super.onInterceptTouchEvent(ev);
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (!isEnabled()) {
            return true;
        }
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    public void setSize(long size) {
        currentSize = size;
        sizeTextView.setText(LocaleController.formatString("AutodownloadSizeLimitUpTo", R.string.AutodownloadSizeLimitUpTo, AndroidUtilities.formatFileSize(size)));

        float progress = 0.0f;
        size -= 500 * 1024;
        if (size < 524 * 1024) {
            progress = Math.max(0, size / (float) (524 * 1024)) * 0.25f;
        } else {
            progress += 0.25f;
            size -= 524 * 1024;

            if (size < 1024 * 1024 * 9) {
                progress += Math.max(0, size / (float) (9 * 1024 * 1024)) * 0.25f;
            } else {
                progress += 0.25f;
                size -= 9 * 1024 * 1024;

                if (size < 1024 * 1024 * 90) {
                    progress += Math.max(0, size / (float) (90 * 1024 * 1024)) * 0.25f;
                } else {
                    progress += 0.25f;
                    size -= 90 * 1024 * 1024;

                    progress += Math.max(0, size / (float) (FileLoader.DEFAULT_MAX_FILE_SIZE - 100 * 1024 * 1024)) * 0.25f;
                }
            }
        }
        seekBarView.setProgress(Math.min(1.0f, progress));
    }

    public void setEnabled(boolean value, ArrayList<Animator> animators) {
        super.setEnabled(value);
        if (animators != null) {
            animators.add(ObjectAnimator.ofFloat(textView, "alpha", value ? 1.0f : 0.5f));
            animators.add(ObjectAnimator.ofFloat(seekBarView, "alpha", value ? 1.0f : 0.5f));
            animators.add(ObjectAnimator.ofFloat(sizeTextView, "alpha", value ? 1.0f : 0.5f));
        } else {
            textView.setAlpha(value ? 1.0f : 0.5f);
            seekBarView.setAlpha(value ? 1.0f : 0.5f);
            sizeTextView.setAlpha(value ? 1.0f : 0.5f);
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawLine(LocaleController.isRTL ? 0 : AndroidUtilities.dp(20), getMeasuredHeight() - 1, getMeasuredWidth() - (LocaleController.isRTL ? AndroidUtilities.dp(20) : 0), getMeasuredHeight() - 1, Theme.dividerPaint);
    }
}
