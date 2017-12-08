/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2017.
 */

package org.telegram.ui.Components;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Editable;
import android.text.Layout;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

public class EditTextCaption extends EditTextBoldCursor {

    private String caption;
    private StaticLayout captionLayout;
    private int userNameLength;
    private int xOffset;
    private int yOffset;
    private int triesCount = 0;
    private boolean copyPasteShowed;
    private int hintColor;

    public EditTextCaption(Context context) {
        super(context);
    }

    public void setCaption(String value) {
        if ((caption == null || caption.length() == 0) && (value == null || value.length() == 0) || caption != null && value != null && caption.equals(value)) {
            return;
        }
        caption = value;
        if (caption != null) {
            caption = caption.replace('\n', ' ');
        }
        requestLayout();
    }

    private void makeSelectedBold() {
        applyTextStyleToSelection(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")));
    }

    private void makeSelectedItalic() {
        applyTextStyleToSelection(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/ritalic.ttf")));
    }

    private void makeSelectedRegular() {
        applyTextStyleToSelection(null);
    }

    private void applyTextStyleToSelection(TypefaceSpan span) {
        int start = getSelectionStart();
        int end = getSelectionEnd();
        Editable editable = getText();

        URLSpanUserMention spansMentions[] = editable.getSpans(start, end, URLSpanUserMention.class);
        if (spansMentions != null && spansMentions.length > 0) {
            return;
        }

        TypefaceSpan spans[] = editable.getSpans(start, end, TypefaceSpan.class);
        if (spans != null && spans.length > 0) {
            for (int a = 0; a < spans.length; a++) {
                TypefaceSpan oldSpan = spans[a];
                int spanStart = editable.getSpanStart(oldSpan);
                int spanEnd = editable.getSpanEnd(oldSpan);
                editable.removeSpan(oldSpan);
                if (spanStart < start) {
                    editable.setSpan(new TypefaceSpan(oldSpan.getTypeface()), spanStart, start, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
                if (spanEnd > end) {
                    editable.setSpan(new TypefaceSpan(oldSpan.getTypeface()), end, spanEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            }
        }
        if (span != null) {
            editable.setSpan(span, start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {
        if (Build.VERSION.SDK_INT < 23 && !hasWindowFocus && copyPasteShowed) {
            return;
        }
        super.onWindowFocusChanged(hasWindowFocus);
    }

    private ActionMode.Callback overrideCallback(final ActionMode.Callback callback) {
        return new ActionMode.Callback() {
            @Override
            public boolean onCreateActionMode(ActionMode mode, Menu menu) {
                copyPasteShowed = true;
                return callback.onCreateActionMode(mode, menu);
            }

            @Override
            public boolean onPrepareActionMode(ActionMode mode, Menu menu) {
                return callback.onPrepareActionMode(mode, menu);
            }

            @Override
            public boolean onActionItemClicked(ActionMode mode, MenuItem item) {
                if (item.getItemId() == R.id.menu_regular) {
                    makeSelectedRegular();
                    mode.finish();
                    return true;
                } else if (item.getItemId() == R.id.menu_bold) {
                    makeSelectedBold();
                    mode.finish();
                    return true;
                } else if (item.getItemId() == R.id.menu_italic) {
                    makeSelectedItalic();
                    mode.finish();
                    return true;
                }
                try {
                    return callback.onActionItemClicked(mode, item);
                } catch (Exception ignore) {

                }
                return true;
            }

            @Override
            public void onDestroyActionMode(ActionMode mode) {
                copyPasteShowed = false;
                callback.onDestroyActionMode(mode);
            }
        };
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback, int type) {
        return super.startActionMode(overrideCallback(callback), type);
    }

    @Override
    public ActionMode startActionMode(final ActionMode.Callback callback) {
        return super.startActionMode(overrideCallback(callback));
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        try {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        } catch (Exception e) {
            setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), AndroidUtilities.dp(51));
            FileLog.e(e);
        }

        captionLayout = null;

        if (caption != null && caption.length() > 0) {
            CharSequence text = getText();
            if (text.length() > 1 && text.charAt(0) == '@') {
                int index = TextUtils.indexOf(text, ' ');
                if (index != -1) {
                    TextPaint paint = getPaint();
                    CharSequence str = text.subSequence(0, index + 1);
                    int size = (int) Math.ceil(paint.measureText(text, 0, index + 1));
                    int width = getMeasuredWidth() - getPaddingLeft() - getPaddingRight();
                    userNameLength = str.length();
                    CharSequence captionFinal = TextUtils.ellipsize(caption, paint, width - size, TextUtils.TruncateAt.END);
                    xOffset = size;
                    try {
                        captionLayout = new StaticLayout(captionFinal, getPaint(), width - size, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        if (captionLayout.getLineCount() > 0) {
                            xOffset += -captionLayout.getLineLeft(0);
                        }
                        yOffset = (getMeasuredHeight() - captionLayout.getLineBottom(0)) / 2 + AndroidUtilities.dp(0.5f);
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                }
            }
        }
    }

    public String getCaption() {
        return caption;
    }

    @Override
    public void setHintColor(int value) {
        super.setHintColor(value);
        hintColor = value;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        try {
            if (captionLayout != null && userNameLength == length()) {
                Paint paint = getPaint();
                int oldColor = getPaint().getColor();
                paint.setColor(hintColor);
                canvas.save();
                canvas.translate(xOffset, yOffset);
                captionLayout.draw(canvas);
                canvas.restore();
                paint.setColor(oldColor);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
