package org.telegram.ui.Cells;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.AndroidUtilities.lerp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextUtils;
import android.text.style.CharacterStyle;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.ButtonBounce;
import org.telegram.ui.Components.LinkPath;
import org.telegram.ui.Components.LinkSpanDrawable;
import org.telegram.ui.Components.ReplyMessageLine;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.Text;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanMono;
import org.telegram.ui.GradientClip;

import java.util.Locale;

class ChatMessageFactCheck {

    private final ChatMessageCell cell;

    boolean visible;
    private Text title, what;
    ButtonBounce whatBounce;
    private ButtonBounce bounce;
    StaticLayout textLayout;
    private StaticLayout footerLayout;
    LinkSpanDrawable.LinkCollector links;
    LinkSpanDrawable pressedLink;
    private boolean lastLineEnd;
    private Drawable arrow;
    private int arrowColor;
    private boolean large;
    int textLayoutLeft;
    private int textLayoutHeight;
    private int footerLayoutLeft;
    int contentHeight;
    int contentWidth;
    private ReplyMessageLine messageLine;
    private boolean whatPressed;
    private boolean pressed;

    ChatMessageFactCheck(ChatMessageCell cell) {
        this.cell = cell;
    }

    boolean checkMotionEvent(MotionEvent event) {
        if (!visible || title == null || what == null) {
            return false;
        }
        if (whatBounce == null) {
            whatBounce = new ButtonBounce(cell);
        }
        if (bounce == null) {
            bounce = new ButtonBounce(cell);
            bounce.setAdditionalInvalidate(cell::invalidateOutbounds);
        }

        final float right = cell.getBackgroundDrawableRight() + (cell.transitionParams != null ? cell.transitionParams.deltaRight : 0) - dp(10 + (cell.currentMessageObject.isOutOwner() && !cell.mediaBackground && !cell.drawPinnedBottom ? 6 : 0)) - cell.getExtraTextX();
        int x, y;
        if (cell.currentMessagesGroup != null && !cell.currentMessagesGroup.isDocuments) {
            x = (int) cell.captionX;
        } else if (cell.currentMessageObject.isOutOwner()) {
            x = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
            if (cell.currentMessageObject.type == MessageObject.TYPE_EMOJIS) {
                x -= Math.max(0, x + Math.max(cell.replyNameWidth, cell.replyTextWidth) + dp(14) - AndroidUtilities.displaySize.x);
            }
        } else {
            if (cell.mediaBackground) {
                x = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
            } else {
                x = cell.backgroundDrawableLeft + dp(cell.drawPinnedBottom ? 12 : 18) + cell.getExtraTextX();
            }
        }
        x += cell.transitionParams.deltaLeft;
        x -= dp(1.33f);
        y = cell.factCheckY;
        final int width = (int) (right - x);

        final int ex = (int) cell.getEventX(event);
        final int ey = (int) cell.getEventY(event);

        final int bx = (int) (x + dp(10) + title.getCurrentWidth() + dp(4));
        final int by = (int) (y + dp(4.33f));

        AndroidUtilities.rectTmp.set(bx, by, bx + what.getCurrentWidth() + dp(4), by + dp(17.33f));
        final boolean whatHit = AndroidUtilities.rectTmp.contains(cell.getEventX(event), cell.getEventY(event));
        AndroidUtilities.rectTmp.set(x, y, x + width, y + contentHeight);
        boolean allHit = !whatHit && !cell.hadLongPress && pressedLink == null && AndroidUtilities.rectTmp.contains(cell.getEventX(event), cell.getEventY(event));

        if ((allHit && !cell.hadLongPress || pressedLink != null) && textLayout != null) {
            if (links == null) {
                links = new LinkSpanDrawable.LinkCollector(cell);
                links.setAdditionalInvalidate(cell::invalidateOutbounds);
            }
            final int textX = x + dp(10) - textLayoutLeft;
            final int textY = y + dp(22);
            if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_UP && pressedLink != null) {
                if (ex >= textX && ey >= textY && ex <= textX + width && ey <= textY + textLayout.getHeight()) {
                    try {
                        final int line = textLayout.getLineForVertical(ey - textY);
                        final int off = textLayout.getOffsetForHorizontal(line, ex - textX);

                        final float left = textLayout.getLineLeft(line);
                        if (left <= ex - textX && left + textLayout.getLineWidth(line) >= ex - textX) {
                            Spannable buffer = (Spannable) textLayout.getText();
                            CharacterStyle[] link = buffer.getSpans(off, off, ClickableSpan.class);
                            boolean isMono = false;
                            if (link == null || link.length == 0) {
                                link = buffer.getSpans(off, off, URLSpanMono.class);
                                isMono = true;
                            }
                            boolean ignore = false;
                            if (link.length == 0 || link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                ignore = true;
                            }
                            if (!ignore && !AndroidUtilities.isAccessibilityScreenReaderEnabled()) {
                                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                    if (pressedLink == null || pressedLink.getSpan() != link[0]) {
                                        links.removeLink(pressedLink);
                                        pressedLink = new LinkSpanDrawable(link[0], cell.resourcesProvider, ex - textX, ey - textY, cell.spanSupportsLongPress(link[0]));
                                        if (messageLine != null) {
                                            pressedLink.setColor(Theme.multAlpha(messageLine.getColor(), .10f));
                                        }
                                        try {
                                            LinkPath path = pressedLink.obtainNewPath();
                                            int[] pos = cell.getRealSpanStartAndEnd(buffer, pressedLink.getSpan());
                                            path.setCurrentLayout(textLayout, pos[0], 0);
                                            textLayout.getSelectionPath(pos[0], pos[1], path);
                                        } catch (Exception e) {
                                            FileLog.e(e);
                                        }
                                        links.addLink(pressedLink, 1);
                                        allHit = false;
                                    }
                                    cell.invalidate();
                                } else {
                                    if (pressedLink != null && link[0] == pressedLink.getSpan()) {
                                        cell.delegate.didPressUrl(cell, pressedLink.getSpan(), false);
                                        cell.resetPressedLink(1);
                                        whatPressed = false;
                                        pressed = false;
                                        pressedLink = null;
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                } else {
                    cell.resetPressedLink(1);
                }
            }
        }

        allHit = large && allHit;

        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            whatPressed = whatHit;
            pressed = allHit;
        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
            whatPressed = whatHit;
            pressed = allHit;
        } else if (event.getAction() == MotionEvent.ACTION_UP) {
            if (whatPressed) {
                if (cell.delegate != null) {
                    cell.delegate.didPressFactCheckWhat(cell, (int) (bx + dp(4) + what.getCurrentWidth() / 2f), by);
                }
            } else if (pressed && !cell.transitionParams.animateFactCheckExpanded) {
                if (cell.delegate != null) {
                    cell.delegate.didPressFactCheck(cell);
                }
            }
            whatPressed = false;
            pressed = false;
        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
            whatPressed = false;
            pressed = false;
            if (links != null) {
                links.clear();
            }
            pressedLink = null;
        }

        whatBounce.setPressed(whatPressed);
        bounce.setPressed(pressed);
        return whatPressed || pressed || pressedLink != null;
    }

    void draw(Canvas canvas, float alpha) {
        if (cell.currentPosition != null && !cell.currentPosition.last) {
            return;
        }
        float visibleAlpha = visible ? 1f : 0f;
        if (cell.transitionParams.animateFactCheck) {
            visibleAlpha = AndroidUtilities.lerp(1f - visibleAlpha, visibleAlpha, cell.transitionParams.animateChangeProgress);
        }
        alpha *= visibleAlpha;
        if (alpha <= 0)
            return;

        final float right = cell.getBackgroundDrawableRight() + (cell.transitionParams != null ? cell.transitionParams.deltaRight : 0) - dp(10 + (cell.currentMessageObject.isOutOwner() && !cell.mediaBackground && !cell.drawPinnedBottom ? 6 : 0)) - cell.getExtraTextX();

        int x, y;
        if (cell.currentMessagesGroup != null && !cell.currentMessagesGroup.isDocuments) {
            x = (int) cell.captionX;
        } else if (cell.currentMessageObject.isOutOwner()) {
            x = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
            if (cell.currentMessageObject.type == MessageObject.TYPE_EMOJIS) {
                x -= Math.max(0, x + Math.max(cell.replyNameWidth, cell.replyTextWidth) + dp(14) - AndroidUtilities.displaySize.x);
            }
        } else {
            if (cell.mediaBackground) {
                x = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
            } else {
                x = cell.backgroundDrawableLeft + dp(cell.drawPinnedBottom ? 12 : 18) + cell.getExtraTextX();
            }
        }
        x += cell.transitionParams.deltaLeft;
        x -= dp(1.33f);
        y = cell.factCheckY;
//        if (transitionParams.animateLinkPreviewY) {
//            final int animatedStartY = AndroidUtilities.lerp(transitionParams.animateFromLinkPreviewY, startY, transitionParams.animateChangeProgress);
//            linkPreviewYDiff = animatedStartY - startY;
//            y = animatedStartY;
//        }

        final int width = (int) (right - x);
        int smallImageStartY = 0;

        int height = contentHeight;
        if (cell.transitionParams.animateFactCheckHeight) {
            height = AndroidUtilities.lerp(cell.transitionParams.animateFactCheckHeightFrom, contentHeight, cell.transitionParams.animateChangeProgress);
        }

        float expanded = !large || cell.getPrimaryMessageObject() != null && cell.getPrimaryMessageObject().factCheckExpanded ? 1f : 0f;
        if (cell.transitionParams.animateFactCheckExpanded) {
            expanded = AndroidUtilities.lerp(1f - expanded, expanded, cell.transitionParams.animateChangeProgress);
        }

        if (messageLine == null) {
            messageLine = new ReplyMessageLine(cell);
        }
        int textColor = messageLine.setFactCheck(cell.resourcesProvider);

        canvas.save();
        canvas.translate(x, y);

        AndroidUtilities.rectTmp.set(0, 0, width, height);
        final float s = bounce != null ? bounce.getScale(0.01f) : 1f;
        canvas.scale(s, s, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());

        messageLine.drawBackground(canvas, AndroidUtilities.rectTmp, 5, 5, 5, alpha, false, false);
        messageLine.drawLine(canvas, AndroidUtilities.rectTmp, alpha);

        if (title != null) {
            title.draw(canvas, dp(10), dp(12), textColor, alpha);
            if (what != null) {
                final int bx = (int) (dp(10) + title.getCurrentWidth() + dp(4));
                AndroidUtilities.rectTmp.set(bx, dp(4.33f), bx + dp(10) + what.getCurrentWidth(), dp(4.33f + 17.33f));
                final float s2 = whatBounce != null ? whatBounce.getScale(0.1f) : 1f;
                canvas.save();
                canvas.scale(s2, s2, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());
                canvas.drawRoundRect(AndroidUtilities.rectTmp, dp(17), dp(17), messageLine.backgroundPaint);
                what.draw(canvas, bx + dp(5), dp(12), textColor, alpha);
                canvas.restore();
            }
        }

        canvas.restore();
    }

    void drawText(Canvas canvas, float alpha) {
        if (cell.currentPosition != null && !cell.currentPosition.last) {
            return;
        }
        float visibleAlpha = visible ? 1f : 0f;
        if (cell.transitionParams.animateFactCheck) {
            visibleAlpha = AndroidUtilities.lerp(1f - visibleAlpha, visibleAlpha, cell.transitionParams.animateChangeProgress);
        }
        alpha *= visibleAlpha;
        if (alpha <= 0)
            return;

        final float right = cell.getBackgroundDrawableRight() + (cell.transitionParams != null ? cell.transitionParams.deltaRight : 0) - dp(10 + (cell.currentMessageObject.isOutOwner() && !cell.mediaBackground && !cell.drawPinnedBottom ? 6 : 0)) - cell.getExtraTextX();

        int x, y;
        if (cell.currentMessagesGroup != null && !cell.currentMessagesGroup.isDocuments) {
            x = (int) cell.captionX;
        } else if (cell.currentMessageObject.isOutOwner()) {
            x = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
            if (cell.currentMessageObject.type == MessageObject.TYPE_EMOJIS) {
                x -= Math.max(0, x + Math.max(cell.replyNameWidth, cell.replyTextWidth) + dp(14) - AndroidUtilities.displaySize.x);
            }
        } else {
            if (cell.mediaBackground) {
                x = cell.backgroundDrawableLeft + dp(12) + cell.getExtraTextX();
            } else {
                x = cell.backgroundDrawableLeft + dp(cell.drawPinnedBottom ? 12 : 18) + cell.getExtraTextX();
            }
        }
        x += cell.transitionParams.deltaLeft;
        x -= dp(1.33f);
        y = cell.factCheckY;

        final int width = (int) (right - x);
        int smallImageStartY = 0;

        int height = contentHeight;
        if (cell.transitionParams.animateFactCheckHeight) {
            height = AndroidUtilities.lerp(cell.transitionParams.animateFactCheckHeightFrom, contentHeight, cell.transitionParams.animateChangeProgress);
        }

        float expanded = !large || cell.getPrimaryMessageObject() != null && cell.getPrimaryMessageObject().factCheckExpanded ? 1f : 0f;
        if (cell.transitionParams.animateFactCheckExpanded) {
            expanded = AndroidUtilities.lerp(1f - expanded, expanded, cell.transitionParams.animateChangeProgress);
        }

        if (messageLine == null) {
            messageLine = new ReplyMessageLine(cell);
        }
        int textColor = messageLine.setFactCheck(cell.resourcesProvider);

        canvas.save();
        canvas.translate(x, y);

        AndroidUtilities.rectTmp.set(0, 0, width, height);
        final float s = bounce != null ? bounce.getScale(0.01f) : 1f;
        canvas.scale(s, s, AndroidUtilities.rectTmp.centerX(), AndroidUtilities.rectTmp.centerY());

        if (textLayout != null) {
            if (large) {
                canvas.saveLayerAlpha(0, 0, width, height - 1, 0xFF, Canvas.ALL_SAVE_FLAG);
            }
            canvas.save();
            canvas.translate(dp(10) - textLayoutLeft, dp(22));
            Theme.chat_replyTextPaint.linkColor = textColor;
            if (links != null && links.draw(canvas)) {
                cell.invalidateOutbounds();
            }
            if (cell.currentMessageObject.isOutOwner()) {
                Theme.chat_replyTextPaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextOut));
            } else {
                Theme.chat_replyTextPaint.setColor(cell.getThemedColor(Theme.key_chat_messageTextIn));
            }
            int oldAlpha = Theme.chat_replyTextPaint.getAlpha();
            Theme.chat_replyTextPaint.setAlpha((int) (oldAlpha * alpha));
            if (cell.delegate != null && cell.delegate.getTextSelectionHelper() != null && cell.getDelegate().getTextSelectionHelper().isSelected(cell.currentMessageObject)) {
                cell.delegate.getTextSelectionHelper().drawFactCheck(cell.currentMessageObject.isOutOwner(), textLayout, canvas);
            }
            textLayout.draw(canvas);
            canvas.restore();
            Theme.chat_replyTextPaint.setAlpha(oldAlpha);

            AndroidUtilities.rectTmp.set(dp(10), dp(22) + textLayoutHeight + dp(6.33f), width - dp(10), dp(22) + textLayoutHeight + dp(6.33f) + 1);
            Theme.chat_titleLabelTextPaint.setColor(textColor);
            Theme.chat_titleLabelTextPaint.setAlpha((int) (Theme.chat_titleLabelTextPaint.getAlpha() * .5f * alpha));
            canvas.drawRect(AndroidUtilities.rectTmp, Theme.chat_titleLabelTextPaint);

            canvas.save();
            canvas.translate(dp(10) - footerLayoutLeft, dp(22) + textLayoutHeight + dp(12.66f));
            Theme.chat_titleLabelTextPaint.setColor(textColor);
            Theme.chat_titleLabelTextPaint.setAlpha((int) (Theme.chat_titleLabelTextPaint.getAlpha() * alpha));
            footerLayout.draw(canvas);
            canvas.restore();
            if (cell.clip == null) {
                cell.clip = new GradientClip();
            }
            if (large) {
                canvas.save();
                final int sz = dp(22) + textLayoutHeight + dp(12.66f) < height ? dp(20) : dp(24);
                AndroidUtilities.rectTmp.set(0, height - dp(6.66f) - sz, width, height);
                cell.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.BOTTOM, (1f - expanded) * alpha);
                AndroidUtilities.rectTmp.set(width - dp(60), height - sz, width - dp(32), height);
                cell.clip.draw(canvas, AndroidUtilities.rectTmp, GradientClip.RIGHT, (1f - expanded) * alpha);
                AndroidUtilities.rectTmp.set(width - dp(32), height - sz, width, height);
                canvas.drawRect(AndroidUtilities.rectTmp, cell.clip.getPaint(GradientClip.RIGHT, (1f - expanded) * alpha));
                canvas.restore();
                canvas.restore();
            }
        }

        if (large) {
            if (arrow == null) {
                arrow = cell.getContext().getResources().getDrawable(R.drawable.arrow_more).mutate();
                arrow.setColorFilter(new PorterDuffColorFilter(arrowColor = textColor, PorterDuff.Mode.SRC_IN));
            }
            if (textColor != arrowColor) {
                arrow.setColorFilter(new PorterDuffColorFilter(arrowColor = textColor, PorterDuff.Mode.SRC_IN));
            }

            canvas.save();
            final int sz = dp(16);
            arrow.setBounds(
                width - sz - dp(4 + 3),
                height - sz - dp(2 + 3),
                width - dp(4 + 3),
                height - dp(2 + 3)
            );
            canvas.rotate(AndroidUtilities.lerp(0, 180, expanded), arrow.getBounds().centerX(), arrow.getBounds().centerY());
            arrow.setAlpha((int) (0xFF * alpha));
            arrow.draw(canvas);
            canvas.restore();
        }

        canvas.restore();
    }

    private float visibleAlpha() {
        if (cell.transitionParams.animateFactCheck) {
            return AndroidUtilities.lerp(visible ? 0f : 1f, visible ? 1f : 0f, cell.transitionParams.animateChangeProgress);
        }
        return visible ? 1f : 0f;
    }

    // Whether layout() can find a fact check. When false, layout() would only reset to defaults and return 0,
    // so the cell skips creating this object. Doesn't call getFactCheck(): it has side effects and layout() calls it.
    static boolean isNeeded(ChatMessageCell cell) {
        final MessageObject msg = cell.getPrimaryMessageObject();
        return (cell.currentPosition == null || (cell.currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) && msg != null && msg.messageOwner != null && msg.messageOwner.factcheck != null;
    }

    int layout(final int maxTextWidth) {
        MessageObject msg = cell.getPrimaryMessageObject();
        int addheight = 0;
        contentHeight = 0;
        contentWidth = 0;
        visible = (cell.currentPosition == null || (cell.currentPosition.flags & MessageObject.POSITION_FLAG_BOTTOM) != 0) && msg != null && msg.getFactCheck() != null && !msg.isRepostPreview;
        if (visible) {
            TLRPC.TL_factCheck factCheck = msg.getFactCheck();
            CharSequence text = msg.getFactCheckText();
            if (factCheck.need_check || text == null) {
                visible = false;
            } else {
                contentHeight += dp(4.66f);

                title = new Text(getString(R.string.FactCheck), 14, AndroidUtilities.bold());
                what = new Text(getString(R.string.FactCheckWhat), 11);
                contentHeight += dp(17.33f);
                contentWidth = (int) (dp(20) + title.getCurrentWidth() + what.getCurrentWidth() + dp(18));

                String country;
                try {
                    country = new Locale("", factCheck.country).getDisplayCountry(LocaleController.getInstance().getCurrentLocale());
                } catch (Exception e) {
                    FileLog.e(e);
                    country = factCheck.country;
                }

                textLayout = StaticLayoutEx.createStaticLayout(text, Theme.chat_replyTextPaint, maxTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, maxTextWidth, 99999);
                footerLayout = StaticLayoutEx.createStaticLayout(formatString(R.string.FactCheckFooter, country), Theme.chat_titleLabelTextPaint, maxTextWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, dp(1), false, TextUtils.TruncateAt.END, maxTextWidth, 99999);
                textLayoutLeft = textLayout.getWidth();
                int textLayoutRight = 0;
                for (int a = 0; a < textLayout.getLineCount(); a++) {
                    textLayoutLeft = (int) Math.min(textLayoutLeft, textLayout.getLineLeft(a));
                    textLayoutRight = (int) Math.max(textLayoutRight, textLayout.getLineRight(a));
                }
                int textLayoutWidth = Math.abs(textLayoutRight - textLayoutLeft);
//                factCheckTextLayoutLastLineEnd = factCheckLarge && factCheckTextLayout.getLineRight(factCheckTextLayout.getLineCount() - 1) - factCheckTextLayoutLeft > factCheckTextLayoutWidth - dp(30);
                contentWidth = Math.max(contentWidth, textLayoutWidth + dp(20));

                footerLayoutLeft = footerLayout.getWidth();
                int footerLayoutRight = 0;
                for (int a = 0; a < footerLayout.getLineCount(); a++) {
                    footerLayoutLeft = (int) Math.min(footerLayoutLeft, footerLayout.getLineLeft(a));
                    footerLayoutRight = (int) Math.max(footerLayoutRight, footerLayout.getLineRight(a));
                }
                int footerLayoutWidth = Math.abs(footerLayoutRight - footerLayoutLeft);
                contentWidth = Math.max(contentWidth, footerLayoutWidth + dp(20));

                int height = (textLayoutHeight = textLayout.getLineBottom(textLayout.getLineCount() - 1)) + dp(12.66f) + footerLayout.getLineBottom(footerLayout.getLineCount() - 1);
                int limit = (int) (3.5f * Theme.chat_replyTextPaint.getTextSize() * 1.4f);
                large = textLayout.getLineCount() > 3 && height + dp(10) > limit;
                lastLineEnd = large && footerLayout.getLineRight(footerLayout.getLineCount() - 1) - footerLayoutLeft > contentWidth - dp(50);
                if (large && !msg.factCheckExpanded) {
                    if (height < limit) {
                        large = false;
                    }
                    height = Math.min(limit, height);
                }
                if (lastLineEnd) {
                    height += Theme.chat_replyTextPaint.getTextSize() * 1.3f;
                }
                contentHeight += height;

                contentHeight += dp(6.66f);

                addheight += dp(2);
            }
        }
        return visible ? contentHeight + addheight : 0;
    }
}
