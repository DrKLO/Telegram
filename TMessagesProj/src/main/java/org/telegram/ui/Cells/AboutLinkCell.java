/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Build;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.Gravity;
import android.view.MotionEvent;
import android.widget.FrameLayout;
import android.widget.ImageView;

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
    private TextPaint textPaint;
    private Paint urlPaint;
    private String oldText;
    private int textX;
    private int textY;
    private SpannableStringBuilder stringBuilder;

    private ImageView imageView;

    private ClickableSpan pressedLink;
    private LinkPath urlPath = new LinkPath();

    private AboutLinkCellDelegate delegate;

    public interface AboutLinkCellDelegate {
        void didPressUrl(String url);
    }

    public AboutLinkCell(Context context) {
        super(context);

        textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setTextSize(AndroidUtilities.dp(16));
        textPaint.setColor(0xff000000);
        textPaint.linkColor = Theme.MSG_LINK_TEXT_COLOR;

        urlPaint = new Paint();
        urlPaint.setColor(Theme.MSG_LINK_SELECT_BACKGROUND_COLOR);

        imageView = new ImageView(context);
        imageView.setScaleType(ImageView.ScaleType.CENTER);
        addView(imageView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, (LocaleController.isRTL ? Gravity.RIGHT : Gravity.LEFT) | Gravity.TOP, LocaleController.isRTL ? 0 : 16, 5, LocaleController.isRTL ? 16 : 0, 0));
        setWillNotDraw(false);
    }

    public void setDelegate(AboutLinkCellDelegate botHelpCellDelegate) {
        delegate = botHelpCellDelegate;
    }

    private void resetPressedLink() {
        if (pressedLink != null) {
            pressedLink = null;
        }
        invalidate();
    }

    public void setTextAndIcon(String text, int resId) {
        if (text == null || text.length() == 0) {
            setVisibility(GONE);
            return;
        }
        if (text != null && oldText != null && text.equals(oldText)) {
            return;
        }
        oldText = text;
        stringBuilder = new SpannableStringBuilder(oldText);
        MessageObject.addLinks(stringBuilder, false);
        Emoji.replaceEmoji(stringBuilder, textPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        requestLayout();
        if (resId == 0) {
            imageView.setImageDrawable(null);
        } else {
            imageView.setImageResource(resId);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        if (Build.VERSION.SDK_INT >= 21 && getBackground() != null) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                getBackground().setHotspot(x, y);
            }
        }

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
                                    FileLog.e("tmessages", e);
                                }
                            } else {
                                resetPressedLink();
                            }
                        } else {
                            resetPressedLink();
                        }
                    } catch (Exception e) {
                        resetPressedLink();
                        FileLog.e("tmessages", e);
                    }
                } else if (pressedLink != null) {
                    try {
                        if (pressedLink instanceof URLSpanNoUnderline) {
                            String url = ((URLSpanNoUnderline) pressedLink).getURL();
                            if (url.startsWith("@") || url.startsWith("#") || url.startsWith("/")) {
                                if (delegate != null) {
                                    delegate.didPressUrl(url);
                                }
                            }
                        } else {
                            if (pressedLink instanceof URLSpan) {
                                Browser.openUrl(getContext(), ((URLSpan) pressedLink).getURL());
                            } else {
                                pressedLink.onClick(this);
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
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
        textLayout = new StaticLayout(stringBuilder, textPaint, MeasureSpec.getSize(widthMeasureSpec) - AndroidUtilities.dp(71 + 16), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        super.onMeasure(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(textLayout.getHeight() + AndroidUtilities.dp(16), MeasureSpec.EXACTLY));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(textX = AndroidUtilities.dp(LocaleController.isRTL ? 16 : 71), textY = AndroidUtilities.dp(8));
        if (pressedLink != null) {
            canvas.drawPath(urlPath, urlPaint);
        }
        textLayout.draw(canvas);
        canvas.restore();
    }
}
