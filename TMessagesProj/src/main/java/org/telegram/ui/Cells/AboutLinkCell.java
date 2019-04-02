/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.browser.Browser;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.URLSpanNoUnderline;

public class AboutLinkCell extends FrameLayout {

    private StaticLayout textLayout;
    private String oldText;
    private int textX;
    private int textY;
    private SpannableStringBuilder stringBuilder;
    private TextView valueTextView;

    private ClickableSpan pressedLink;
    private LinkPath urlPath = new LinkPath();

    public AboutLinkCell(Context context) {
        super(context);

        valueTextView = new TextView(context);
        valueTextView.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText2));
        valueTextView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        valueTextView.setLines(1);
        valueTextView.setMaxLines(1);
        valueTextView.setSingleLine(true);
        valueTextView.setGravity(LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT);
        addView(valueTextView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.BOTTOM, 23, 0, 23, 10));

        setWillNotDraw(false);
    }

    protected void didPressUrl(String url) {

    }

    private void resetPressedLink() {
        if (pressedLink != null) {
            pressedLink = null;
        }
        invalidate();
    }

    public void setText(String text, boolean parseLinks) {
        setTextAndValue(text, null, parseLinks);
    }

    public void setTextAndValue(String text, String value, boolean parseLinks) {
        if (TextUtils.isEmpty(text) || text != null && oldText != null && text.equals(oldText)) {
            return;
        }
        oldText = text;
        stringBuilder = new SpannableStringBuilder(oldText);
        if (parseLinks) {
            MessageObject.addLinks(false, stringBuilder, false);
        }
        Emoji.replaceEmoji(stringBuilder, Theme.profile_aboutTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        if (TextUtils.isEmpty(value)) {
            valueTextView.setVisibility(GONE);
        } else {
            valueTextView.setText(value);
            valueTextView.setVisibility(VISIBLE);
        }
        requestLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        boolean result = false;
        if (textLayout != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || pressedLink != null && event.getAction() == MotionEvent.ACTION_UP) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    resetPressedLink();
                    try {
                        int x2 = (int) (x - textX);
                        int y2 = (int) (y - textY);
                        final int line = textLayout.getLineForVertical(y2);
                        final int off = textLayout.getOffsetForHorizontal(line, x2);

                        final float left = textLayout.getLineLeft(line);
                        if (left <= x2 && left + textLayout.getLineWidth(line) >= x2) {
                            Spannable buffer = (Spannable) textLayout.getText();
                            ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                            if (link.length != 0) {
                                resetPressedLink();
                                pressedLink = link[0];
                                result = true;
                                try {
                                    int start = buffer.getSpanStart(pressedLink);
                                    urlPath.setCurrentLayout(textLayout, start, 0);
                                    textLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), urlPath);
                                } catch (Exception e) {
                                    FileLog.e(e);
                                }
                            } else {
                                resetPressedLink();
                            }
                        } else {
                            resetPressedLink();
                        }
                    } catch (Exception e) {
                        resetPressedLink();
                        FileLog.e(e);
                    }
                } else if (pressedLink != null) {
                    try {
                        if (pressedLink instanceof URLSpanNoUnderline) {
                            String url = ((URLSpanNoUnderline) pressedLink).getURL();
                            if (url.startsWith("@") || url.startsWith("#") || url.startsWith("/")) {
                                didPressUrl(url);
                            }
                        } else {
                            if (pressedLink instanceof URLSpan) {
                                Browser.openUrl(getContext(), ((URLSpan) pressedLink).getURL());
                            } else {
                                pressedLink.onClick(this);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    resetPressedLink();
                    result = true;
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                resetPressedLink();
            }
        }
        return result || super.onTouchEvent(event);
    }

    @SuppressLint("DrawAllocation")
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        if (stringBuilder != null) {
            int maxWidth = MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(23 + 23);
            if (Build.VERSION.SDK_INT >= 24) {
                textLayout = StaticLayout.Builder.obtain(stringBuilder, 0, stringBuilder.length(), Theme.profile_aboutTextPaint, maxWidth)
                        .setBreakStrategy(StaticLayout.BREAK_STRATEGY_HIGH_QUALITY)
                        .setHyphenationFrequency(StaticLayout.HYPHENATION_FREQUENCY_NONE)
                        .setAlignment(LocaleController.isRTL ? Layout.Alignment.ALIGN_RIGHT : Layout.Alignment.ALIGN_LEFT)
                        .build();
            } else {
                textLayout = new StaticLayout(stringBuilder, Theme.profile_aboutTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            }
        }
        int height = (textLayout != null ? textLayout.getHeight() : AndroidUtilities.dp(20)) + AndroidUtilities.dp(16);
        if (valueTextView.getVisibility() == VISIBLE) {
            height += AndroidUtilities.dp(23);
        }
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(textX = AndroidUtilities.dp(23), textY = AndroidUtilities.dp(8));
        if (pressedLink != null) {
            canvas.drawPath(urlPath, Theme.linkSelectionPaint);
        }
        try {
            if (textLayout != null) {
                textLayout.draw(canvas);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        canvas.restore();
    }
}
