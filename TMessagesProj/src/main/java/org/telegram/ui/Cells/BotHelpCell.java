/*
 * This is the source code of Telegram for Android v. 5.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2018.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.StaticLayout;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.view.MotionEvent;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.TypefaceSpan;
import org.telegram.ui.Components.URLSpanNoUnderline;

public class BotHelpCell extends View {

    private StaticLayout textLayout;
    private String oldText;

    private int width;
    private int height;
    private int textX;
    private int textY;
    public boolean wasDraw;

    private ClickableSpan pressedLink;
    private LinkPath urlPath = new LinkPath();

    private BotHelpCellDelegate delegate;
    private final Theme.ResourcesProvider resourcesProvider;

    private boolean animating;

    public interface BotHelpCellDelegate {
        void didPressUrl(String url);
    }

    public BotHelpCell(Context context, Theme.ResourcesProvider resourcesProvider) {
        super(context);
        this.resourcesProvider = resourcesProvider;
    }

    public void setDelegate(BotHelpCellDelegate botHelpCellDelegate) {
        delegate = botHelpCellDelegate;
    }

    private void resetPressedLink() {
        if (pressedLink != null) {
            pressedLink = null;
        }
        invalidate();
    }

    public void setText(boolean bot, String text) {
        if (text == null || text.length() == 0) {
            setVisibility(GONE);
            return;
        }
        if (text != null && text.equals(oldText)) {
            return;
        }
        oldText = AndroidUtilities.getSafeString(text);
        setVisibility(VISIBLE);
        int maxWidth;
        if (AndroidUtilities.isTablet()) {
            maxWidth = (int) (AndroidUtilities.getMinTabletSide() * 0.7f);
        } else {
            maxWidth = (int) (Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) * 0.7f);
        }
        String[] lines = text.split("\n");
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder();
        String help = LocaleController.getString("BotInfoTitle", R.string.BotInfoTitle);
        if (bot) {
            stringBuilder.append(help);
            stringBuilder.append("\n\n");
        }
        for (int a = 0; a < lines.length; a++) {
            stringBuilder.append(lines[a].trim());
            if (a != lines.length - 1) {
                stringBuilder.append("\n");
            }
        }
        MessageObject.addLinks(false, stringBuilder);
        if (bot) {
            stringBuilder.setSpan(new TypefaceSpan(AndroidUtilities.getTypeface("fonts/rmedium.ttf")), 0, help.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        Emoji.replaceEmoji(stringBuilder, Theme.chat_msgTextPaint.getFontMetricsInt(), AndroidUtilities.dp(20), false);
        try {
            textLayout = new StaticLayout(stringBuilder, Theme.chat_msgTextPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
            width = 0;
            height = textLayout.getHeight() + AndroidUtilities.dp(4 + 18);
            int count = textLayout.getLineCount();
            for (int a = 0; a < count; a++) {
                width = (int) Math.ceil(Math.max(width, textLayout.getLineWidth(a) + textLayout.getLineLeft(a)));
            }
            if (width > maxWidth) {
                width = maxWidth;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        width += AndroidUtilities.dp(4 + 18);
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
                                if (delegate != null) {
                                    delegate.didPressUrl(url);
                                }
                            }
                        } else {
                            if (pressedLink instanceof URLSpan) {
                                if (delegate != null) {
                                    delegate.didPressUrl(((URLSpan) pressedLink).getURL());
                                }
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

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.makeMeasureSpec(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.EXACTLY), height + AndroidUtilities.dp(8));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int x = (getWidth() - width) / 2;
        int y = AndroidUtilities.dp(2);
        Drawable shadowDrawable = Theme.chat_msgInMediaDrawable.getShadowDrawable();
        if (shadowDrawable != null) {
            shadowDrawable.setBounds(x, y, width + x, height + y);
            shadowDrawable.draw(canvas);
        }
        int w = AndroidUtilities.displaySize.x;
        int h = AndroidUtilities.displaySize.y;
        if (getParent() instanceof View) {
            View view = (View) getParent();
            w = view.getMeasuredWidth();
            h = view.getMeasuredHeight();
        }
        Theme.MessageDrawable drawable = (Theme.MessageDrawable) getThemedDrawable(Theme.key_drawable_msgInMedia);
        drawable.setTop((int) getY(), w, h, false, false);
        drawable.setBounds(x, y, width + x, height + y);
        drawable.draw(canvas);
        Theme.chat_msgTextPaint.setColor(getThemedColor(Theme.key_chat_messageTextIn));
        Theme.chat_msgTextPaint.linkColor = getThemedColor(Theme.key_chat_messageLinkIn);
        canvas.save();
        canvas.translate(textX = AndroidUtilities.dp(2 + 9) + x, textY = AndroidUtilities.dp(2 + 9) + y);
        if (pressedLink != null) {
            canvas.drawPath(urlPath, Theme.chat_urlPaint);
        }
        if (textLayout != null) {
            textLayout.draw(canvas);
        }
        canvas.restore();
        wasDraw = true;
    }


    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        wasDraw = false;
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setText(textLayout.getText());
    }

    public boolean animating() {
        return animating;
    }

    public void setAnimating(boolean animating) {
        this.animating = animating;
    }
    
    private int getThemedColor(String key) {
        Integer color = resourcesProvider != null ? resourcesProvider.getColor(key) : null;
        return color != null ? color : Theme.getColor(key);
    }

    private Drawable getThemedDrawable(String drawableKey) {
        Drawable drawable = resourcesProvider != null ? resourcesProvider.getDrawable(drawableKey) : null;
        return drawable != null ? drawable : Theme.getThemeDrawable(drawableKey);
    }
}
