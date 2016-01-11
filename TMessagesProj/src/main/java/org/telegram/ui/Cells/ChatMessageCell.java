/*
 * This is the source code of Telegram for Android v. 1.3.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package org.telegram.ui.Cells;

import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.provider.Browser;
import android.text.Layout;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.text.style.ClickableSpan;
import android.view.MotionEvent;
import android.view.SoundEffectConstants;
import android.view.ViewStructure;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ImageLoader;
import org.telegram.messenger.ImageReceiver;
import org.telegram.messenger.MediaController;
import org.telegram.messenger.FileLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.R;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.RadialProgress;
import org.telegram.ui.Components.ResourceLoader;
import org.telegram.ui.Components.StaticLayoutEx;
import org.telegram.ui.Components.URLSpanBotCommand;
import org.telegram.ui.Components.URLSpanNoUnderline;

import java.io.File;
import java.util.Locale;

public class ChatMessageCell extends ChatBaseCell {

    private int textX, textY;
    private int totalHeight = 0;
    private int linkBlockNum;

    private int lastVisibleBlockNum = 0;
    private int firstVisibleBlockNum = 0;
    private int totalVisibleBlocksCount = 0;

    private RadialProgress radialProgress;
    private ImageReceiver linkImageView;
    private boolean isSmallImage;
    private boolean drawImageButton;
    private boolean isGifDocument;
    private boolean drawLinkImageView;
    private boolean hasLinkPreview;
    private int linkPreviewHeight;
    private boolean isInstagram;
    private int descriptionY;
    private int durationWidth;
    private int descriptionX;
    private int titleX;
    private int authorX;
    private StaticLayout siteNameLayout;
    private StaticLayout titleLayout;
    private StaticLayout descriptionLayout;
    private StaticLayout durationLayout;
    private StaticLayout authorLayout;
    private static TextPaint durationPaint;

    private int buttonX;
    private int buttonY;
    private int buttonState;
    private boolean buttonPressed;
    private boolean photoNotSet;
    private TLRPC.PhotoSize currentPhotoObject;
    private TLRPC.PhotoSize currentPhotoObjectThumb;
    private String currentPhotoFilter;
    private String currentPhotoFilterThumb;
    private boolean cancelLoading;

    private static Drawable igvideoDrawable;

    public ChatMessageCell(Context context) {
        super(context);
        drawForwardedName = true;
        linkImageView = new ImageReceiver(this);
        radialProgress = new RadialProgress(this);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean result = false;
        if (currentMessageObject != null && currentMessageObject.textLayoutBlocks != null && !currentMessageObject.textLayoutBlocks.isEmpty() && currentMessageObject.messageText instanceof Spannable && delegate.canPerformActions()) {
            if (event.getAction() == MotionEvent.ACTION_DOWN || (linkPreviewPressed || pressedLink != null || buttonPressed) && event.getAction() == MotionEvent.ACTION_UP) {
                int x = (int) event.getX();
                int y = (int) event.getY();
                if (x >= textX && y >= textY && x <= textX + currentMessageObject.textWidth && y <= textY + currentMessageObject.textHeight) {
                    y -= textY;
                    int blockNum = Math.max(0, y / currentMessageObject.blockHeight);
                    if (blockNum < currentMessageObject.textLayoutBlocks.size()) {
                        try {
                            MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(blockNum);
                            x -= textX - (int) Math.ceil(block.textXOffset);
                            y -= block.textYOffset;
                            final int line = block.textLayout.getLineForVertical(y);
                            final int off = block.textLayout.getOffsetForHorizontal(line, x) + block.charactersOffset;

                            final float left = block.textLayout.getLineLeft(line);
                            if (left <= x && left + block.textLayout.getLineWidth(line) >= x) {
                                Spannable buffer = (Spannable) currentMessageObject.messageText;
                                ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                                boolean ignore = false;
                                if (link.length == 0 || link.length != 0 && link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                    ignore = true;
                                }
                                if (!ignore) {
                                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                                        resetPressedLink();
                                        pressedLink = link[0];
                                        linkBlockNum = blockNum;
                                        try {
                                            int start = buffer.getSpanStart(pressedLink) - block.charactersOffset;
                                            urlPath.setCurrentLayout(block.textLayout, start);
                                            block.textLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink) - block.charactersOffset, urlPath);
                                        } catch (Exception e) {
                                            FileLog.e("tmessages", e);
                                        }
                                        result = true;
                                    } else {
                                        if (link[0] == pressedLink) {
                                            try {
                                                delegate.didPressUrl(currentMessageObject, pressedLink, false);
                                            } catch (Exception e) {
                                                FileLog.e("tmessages", e);
                                            }
                                            resetPressedLink();
                                            result = true;
                                        }
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
                    } else {
                        resetPressedLink();
                    }
                } else if (hasLinkPreview && x >= textX && x <= textX + backgroundWidth && y >= textY + currentMessageObject.textHeight && y <= textY + currentMessageObject.textHeight + linkPreviewHeight + AndroidUtilities.dp(8)) {
                    if (event.getAction() == MotionEvent.ACTION_DOWN) {
                        resetPressedLink();
                        if (drawLinkImageView && linkImageView.isInsideImage(x, y)) {
                            if (drawImageButton && buttonState != -1 && x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48)) {
                                buttonPressed = true;
                                result = true;
                            } else {
                                linkPreviewPressed = true;
                                result = true;
                            }
                            if (linkPreviewPressed && isGifDocument && buttonState == -1 && MediaController.getInstance().canAutoplayGifs()) {
                                linkPreviewPressed = false;
                                result = false;
                            }
                        } else {
                            if (descriptionLayout != null && y >= descriptionY) {
                                try {
                                    x -= textX + AndroidUtilities.dp(10) + descriptionX;
                                    y -= descriptionY;
                                    final int line = descriptionLayout.getLineForVertical(y);
                                    final int off = descriptionLayout.getOffsetForHorizontal(line, x);

                                    final float left = descriptionLayout.getLineLeft(line);
                                    if (left <= x && left + descriptionLayout.getLineWidth(line) >= x) {
                                        Spannable buffer = (Spannable) currentMessageObject.linkDescription;
                                        ClickableSpan[] link = buffer.getSpans(off, off, ClickableSpan.class);
                                        boolean ignore = false;
                                        if (link.length == 0 || link.length != 0 && link[0] instanceof URLSpanBotCommand && !URLSpanBotCommand.enabled) {
                                            ignore = true;
                                        }
                                        if (!ignore) {
                                            resetPressedLink();
                                            pressedLink = link[0];
                                            linkPreviewPressed = true;
                                            linkBlockNum = -10;
                                            result = true;
                                            try {
                                                int start = buffer.getSpanStart(pressedLink);
                                                urlPath.setCurrentLayout(descriptionLayout, start);
                                                descriptionLayout.getSelectionPath(start, buffer.getSpanEnd(pressedLink), urlPath);
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
                            }
                        }
                    } else if (linkPreviewPressed) {
                        try {
                            if (pressedLink != null) {
                                pressedLink.onClick(this);
                            } else {
                                if (drawImageButton && delegate != null) {
                                    if (isGifDocument) {
                                        if (buttonState == -1) {
                                            buttonState = 2;
                                            currentMessageObject.audioProgress = 1;
                                            linkImageView.setAllowStartAnimation(false);
                                            linkImageView.stopAnimation();
                                            radialProgress.setBackground(getDrawableForCurrentState(), false, false);
                                            invalidate();
                                            playSoundEffect(SoundEffectConstants.CLICK);
                                        } else if (buttonState == 2 || buttonState == 0) {
                                            didPressedButton(false);
                                            playSoundEffect(SoundEffectConstants.CLICK);
                                        }
                                    } else if (buttonState == -1) {
                                        delegate.didClickedImage(this);
                                        playSoundEffect(SoundEffectConstants.CLICK);
                                    }
                                } else {
                                    TLRPC.WebPage webPage = currentMessageObject.messageOwner.media.webpage;
                                    if (Build.VERSION.SDK_INT >= 16 && webPage.embed_url != null && webPage.embed_url.length() != 0) {
                                        delegate.needOpenWebView(webPage.embed_url, webPage.site_name, webPage.url, webPage.embed_width, webPage.embed_height);
                                    } else {
                                        Uri uri = Uri.parse(webPage.url);
                                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                                        intent.putExtra(Browser.EXTRA_APPLICATION_ID, getContext().getPackageName());
                                        getContext().startActivity(intent);
                                    }
                                }
                            }
                        } catch (Exception e) {
                            FileLog.e("tmessages", e);
                        }
                        resetPressedLink();
                        result = true;
                    } else if (buttonPressed) {
                        if (event.getAction() == MotionEvent.ACTION_UP) {
                            buttonPressed = false;
                            playSoundEffect(SoundEffectConstants.CLICK);
                            didPressedButton(false);
                            invalidate();
                        } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                            buttonPressed = false;
                            invalidate();
                        } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                            if (!(x >= buttonX && x <= buttonX + AndroidUtilities.dp(48) && y >= buttonY && y <= buttonY + AndroidUtilities.dp(48))) {
                                buttonPressed = false;
                                invalidate();
                            }
                        }
                    }
                } else {
                    resetPressedLink();
                }
            } else if (event.getAction() == MotionEvent.ACTION_CANCEL) {
                resetPressedLink();
            }
        } else {
            resetPressedLink();
        }
        if (result && event.getAction() == MotionEvent.ACTION_DOWN) {
            startCheckLongPress();
        }
        if (event.getAction() != MotionEvent.ACTION_DOWN && event.getAction() != MotionEvent.ACTION_MOVE) {
            cancelCheckLongPress();
        }
        return result || super.onTouchEvent(event);
    }

    public void setVisiblePart(int position, int height) {
        if (currentMessageObject == null || currentMessageObject.textLayoutBlocks == null) {
            return;
        }
        int newFirst = -1, newLast = -1, newCount = 0;

        for (int a = Math.max(0, (position - textY) / currentMessageObject.blockHeight); a < currentMessageObject.textLayoutBlocks.size(); a++) {
            MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
            float y = textY + block.textYOffset;
            if (intersect(y, y + currentMessageObject.blockHeight, position, position + height)) {
                if (newFirst == -1) {
                    newFirst = a;
                }
                newLast = a;
                newCount++;
            } else if (y > position) {
                break;
            }
        }

        if (lastVisibleBlockNum != newLast || firstVisibleBlockNum != newFirst || totalVisibleBlocksCount != newCount) {
            lastVisibleBlockNum = newLast;
            firstVisibleBlockNum = newFirst;
            totalVisibleBlocksCount = newCount;
            invalidate();
        }
    }

    private boolean intersect(float left1, float right1, float left2, float right2) {
        if (left1 <= left2) {
            return right1 >= left2;
        }
        return left1 <= right2;
    }

    public static StaticLayout generateStaticLayout(CharSequence text, TextPaint paint, int maxWidth, int smallWidth, int linesCount, int maxLines) {
        SpannableStringBuilder stringBuilder = new SpannableStringBuilder(text);
        int addedChars = 0;
        StaticLayout layout = new StaticLayout(text, paint, smallWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        for (int a = 0; a < linesCount; a++) {
            Layout.Directions directions = layout.getLineDirections(a);
            if (layout.getLineLeft(a) != 0 || Build.VERSION.SDK_INT >= 14 && (layout.isRtlCharAt(layout.getLineStart(a)) || layout.isRtlCharAt(layout.getLineEnd(a)))) {
                maxWidth = smallWidth;
            }
            int pos = layout.getLineEnd(a);
            if (pos == text.length()) {
                break;
            }
            pos--;
            if (stringBuilder.charAt(pos + addedChars) == ' ') {
                stringBuilder.replace(pos + addedChars, pos + addedChars + 1, "\n");
            } else if (stringBuilder.charAt(pos + addedChars) != '\n') {
                stringBuilder.insert(pos + addedChars, "\n");
                addedChars++;
            }
            if (a == layout.getLineCount() - 1 || a == maxLines - 1) {
                break;
            }
        }
        return StaticLayoutEx.createStaticLayout(stringBuilder, paint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, maxWidth, maxLines);
    }

    @Override
    protected boolean isUserDataChanged() {
        if (!hasLinkPreview && currentMessageObject.messageOwner.media != null && currentMessageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
            return true;
        }
        //suppress warning
        return super.isUserDataChanged();
    }

    @Override
    public ImageReceiver getPhotoImage() {
        return linkImageView;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        linkImageView.onDetachedFromWindow();
        MediaController.getInstance().removeLoadingFileObserver(this);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (linkImageView.onAttachedToWindow()) {
            updateButtonState(false);
        }
    }

    @Override
    protected void onLongPress() {
        if (pressedLink instanceof URLSpanNoUnderline) {
            URLSpanNoUnderline url = (URLSpanNoUnderline) pressedLink;
            if (url.getURL().startsWith("/")) {
                delegate.didPressUrl(currentMessageObject, pressedLink, true);
                return;
            }
        }
        super.onLongPress();
    }

    @Override
    public void setMessageObject(MessageObject messageObject) {
        boolean dataChanged = currentMessageObject == messageObject && (isUserDataChanged() || photoNotSet);
        if (currentMessageObject != messageObject || dataChanged) {
            if (currentMessageObject != messageObject) {
                firstVisibleBlockNum = 0;
                lastVisibleBlockNum = 0;
            }
            drawLinkImageView = false;
            hasLinkPreview = false;
            resetPressedLink();
            linkPreviewPressed = false;
            buttonPressed = false;
            linkPreviewHeight = 0;
            isInstagram = false;
            durationLayout = null;
            isGifDocument = false;
            descriptionLayout = null;
            titleLayout = null;
            siteNameLayout = null;
            authorLayout = null;
            drawImageButton = false;
            currentPhotoObject = null;
            currentPhotoObjectThumb = null;
            currentPhotoFilter = null;
            int maxWidth;

            if (AndroidUtilities.isTablet()) {
                if (isChat && !messageObject.isOutOwner() && messageObject.messageOwner.from_id > 0) {
                    maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
                    drawName = true;
                } else {
                    drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                    maxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
                }
            } else {
                if (isChat && !messageObject.isOutOwner() && messageObject.messageOwner.from_id > 0) {
                    maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122);
                    drawName = true;
                } else {
                    maxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
                    drawName = messageObject.messageOwner.to_id.channel_id != 0 && !messageObject.isOutOwner();
                }
            }

            backgroundWidth = maxWidth;

            super.setMessageObject(messageObject);

            backgroundWidth = messageObject.textWidth;
            totalHeight = messageObject.textHeight + AndroidUtilities.dp(19.5f) + namesOffset;

            int maxChildWidth = Math.max(backgroundWidth, nameWidth);
            maxChildWidth = Math.max(maxChildWidth, forwardedNameWidth);
            maxChildWidth = Math.max(maxChildWidth, replyNameWidth);
            maxChildWidth = Math.max(maxChildWidth, replyTextWidth);
            int maxWebWidth = 0;

            int timeMore = timeWidth + AndroidUtilities.dp(6);
            if (messageObject.isOutOwner()) {
                timeMore += AndroidUtilities.dp(20.5f);
            }

            if (messageObject.messageOwner.media instanceof TLRPC.TL_messageMediaWebPage && messageObject.messageOwner.media.webpage instanceof TLRPC.TL_webPage) {
                int linkPreviewMaxWidth;
                if (AndroidUtilities.isTablet()) {
                    if (messageObject.messageOwner.from_id > 0 && (currentMessageObject.messageOwner.to_id.channel_id != 0 || currentMessageObject.messageOwner.to_id.chat_id != 0) && !currentMessageObject.isOut()) {
                        linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(122);
                    } else {
                        linkPreviewMaxWidth = AndroidUtilities.getMinTabletSide() - AndroidUtilities.dp(80);
                    }
                } else {
                    if (messageObject.messageOwner.from_id > 0 && (currentMessageObject.messageOwner.to_id.channel_id != 0 || currentMessageObject.messageOwner.to_id.chat_id != 0) && !currentMessageObject.isOutOwner()) {
                        linkPreviewMaxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(122);
                    } else {
                        linkPreviewMaxWidth = Math.min(AndroidUtilities.displaySize.x, AndroidUtilities.displaySize.y) - AndroidUtilities.dp(80);
                    }
                }

                TLRPC.TL_webPage webPage = (TLRPC.TL_webPage) messageObject.messageOwner.media.webpage;

                if (webPage.site_name != null && webPage.photo != null && webPage.site_name.toLowerCase().equals("instagram")) {
                    linkPreviewMaxWidth = Math.max(AndroidUtilities.displaySize.y / 3, currentMessageObject.textWidth);
                }

                int additinalWidth = AndroidUtilities.dp(10);
                int restLinesCount = 3;
                int additionalHeight = 0;
                linkPreviewMaxWidth -= additinalWidth;

                hasLinkPreview = true;

                if (currentMessageObject.photoThumbs == null && webPage.photo != null) {
                    currentMessageObject.generateThumbs(true);
                }

                isSmallImage = webPage.description != null && webPage.type != null && (webPage.type.equals("app") || webPage.type.equals("profile") || webPage.type.equals("article")) && currentMessageObject.photoThumbs != null;

                if (webPage.site_name != null) {
                    try {
                        int width = (int) Math.ceil(replyNamePaint.measureText(webPage.site_name));
                        siteNameLayout = new StaticLayout(webPage.site_name, replyNamePaint, Math.min(width, linkPreviewMaxWidth), Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        int height = siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
                        linkPreviewHeight += height;
                        totalHeight += height;
                        additionalHeight += height;
                        width = siteNameLayout.getWidth();
                        maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                        maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                boolean titleIsRTL = false;
                if (webPage.title != null) {
                    try {
                        titleX = 0;
                        if (linkPreviewHeight != 0) {
                            linkPreviewHeight += AndroidUtilities.dp(2);
                            totalHeight += AndroidUtilities.dp(2);
                        }
                        int restLines = 0;
                        if (!isSmallImage || webPage.description == null) {
                            titleLayout = StaticLayoutEx.createStaticLayout(webPage.title, replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 4);
                        } else {
                            restLines = restLinesCount;
                            titleLayout = generateStaticLayout(webPage.title, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2), restLinesCount, 4);
                            restLinesCount -= titleLayout.getLineCount();
                        }
                        int height = titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
                        linkPreviewHeight += height;
                        totalHeight += height;
                        for (int a = 0; a < titleLayout.getLineCount(); a++) {
                            int lineLeft = (int) titleLayout.getLineLeft(a);
                            if (lineLeft != 0) {
                                titleIsRTL = true;
                                if (titleX == 0) {
                                    titleX = -lineLeft;
                                } else {
                                    titleX = Math.max(titleX, -lineLeft);
                                }
                            }
                            int width;
                            if (lineLeft != 0) {
                                width = titleLayout.getWidth() - lineLeft;
                            } else {
                                width = (int) Math.ceil(titleLayout.getLineWidth(a));
                            }
                            if (a < restLines || lineLeft != 0 && isSmallImage) {
                                width += AndroidUtilities.dp(48 + 2);
                            }
                            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                            maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                boolean authorIsRTL = false;
                if (webPage.author != null) {
                    try {
                        if (linkPreviewHeight != 0) {
                            linkPreviewHeight += AndroidUtilities.dp(2);
                            totalHeight += AndroidUtilities.dp(2);
                        }
                        //int width = Math.min((int) Math.ceil(replyNamePaint.measureText(webPage.author)), linkPreviewMaxWidth);
                        if (restLinesCount == 3 && (!isSmallImage || webPage.description == null)) {
                            authorLayout = new StaticLayout(webPage.author, replyNamePaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                        } else {
                            authorLayout = generateStaticLayout(webPage.author, replyNamePaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2), restLinesCount, 1);
                            restLinesCount -= authorLayout.getLineCount();
                        }
                        int height = authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
                        linkPreviewHeight += height;
                        totalHeight += height;
                        int lineLeft = (int) authorLayout.getLineLeft(0);
                        authorX = -lineLeft;
                        int width;
                        if (lineLeft != 0) {
                            width = authorLayout.getWidth() - lineLeft;
                            authorIsRTL = true;
                        } else {
                            width = (int) Math.ceil(authorLayout.getLineWidth(0));
                        }
                        maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                        maxWebWidth = Math.max(maxWebWidth, width + additinalWidth);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                if (webPage.description != null) {
                    try {
                        descriptionX = 0;
                        currentMessageObject.generateLinkDescription();
                        if (linkPreviewHeight != 0) {
                            linkPreviewHeight += AndroidUtilities.dp(2);
                            totalHeight += AndroidUtilities.dp(2);
                        }
                        int restLines = 0;
                        if (restLinesCount == 3 && !isSmallImage) {
                            descriptionLayout = StaticLayoutEx.createStaticLayout(messageObject.linkDescription, replyTextPaint, linkPreviewMaxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, AndroidUtilities.dp(1), false, TextUtils.TruncateAt.END, linkPreviewMaxWidth, 6);
                        } else {
                            restLines = restLinesCount;
                            descriptionLayout = generateStaticLayout(messageObject.linkDescription, replyTextPaint, linkPreviewMaxWidth, linkPreviewMaxWidth - AndroidUtilities.dp(48 + 2), restLinesCount, 6);
                        }
                        int height = descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
                        linkPreviewHeight += height;
                        totalHeight += height;

                        boolean hasRTL = false;
                        for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
                            int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
                            if (lineLeft != 0) {
                                hasRTL = true;
                                if (descriptionX == 0) {
                                    descriptionX = -lineLeft;
                                } else {
                                    descriptionX = Math.max(descriptionX, -lineLeft);
                                }
                            }
                        }

                        for (int a = 0; a < descriptionLayout.getLineCount(); a++) {
                            int lineLeft = (int) Math.ceil(descriptionLayout.getLineLeft(a));
                            if (lineLeft == 0 && descriptionX != 0) {
                                descriptionX = 0;
                            }

                            int width;
                            if (lineLeft != 0) {
                                width = descriptionLayout.getWidth() - lineLeft;
                            } else {
                                width = hasRTL ? descriptionLayout.getWidth() : (int) Math.ceil(descriptionLayout.getLineWidth(a));
                            }
                            if (a < restLines || restLines != 0 && lineLeft != 0 && isSmallImage) {
                                width += AndroidUtilities.dp(48 + 2);
                            }
                            if (maxWebWidth < width + additinalWidth) {
                                if (titleIsRTL) {
                                    titleX += (width + additinalWidth - maxWebWidth);
                                }
                                if (authorIsRTL) {
                                    authorX += (width + additinalWidth - maxWebWidth);
                                }
                                maxWebWidth = width + additinalWidth;
                            }
                            if (restLines == 0 || !isSmallImage) {
                                if (titleIsRTL) {
                                    titleX = -AndroidUtilities.dp(4);
                                }
                                if (authorIsRTL) {
                                    authorX = -AndroidUtilities.dp(4);
                                }
                            }
                            maxChildWidth = Math.max(maxChildWidth, width + additinalWidth);
                        }
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                    }
                }

                boolean smallImage = webPage.type != null && (webPage.type.equals("app") || webPage.type.equals("profile") || webPage.type.equals("article"));
                if (smallImage && (descriptionLayout == null || descriptionLayout != null && descriptionLayout.getLineCount() == 1)) {
                    smallImage = false;
                    isSmallImage = false;
                }
                int maxPhotoWidth = smallImage ? AndroidUtilities.dp(48) : linkPreviewMaxWidth;

                if (webPage.document != null && MessageObject.isGifDocument(webPage.document)) {
                    if (!MediaController.getInstance().canAutoplayGifs()) {
                        messageObject.audioProgress = 1;
                    }
                    linkImageView.setAllowStartAnimation(messageObject.audioProgress != 1);
                    currentPhotoObject = webPage.document.thumb;
                    isGifDocument = true;
                } else if (webPage.photo != null) {
                    currentPhotoObject = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, drawImageButton ? AndroidUtilities.getPhotoSize() : maxPhotoWidth, !drawImageButton);
                    currentPhotoObjectThumb = FileLoader.getClosestPhotoSizeWithSize(messageObject.photoThumbs, 80);
                    if (currentPhotoObjectThumb == currentPhotoObject) {
                        currentPhotoObjectThumb = null;
                    }
                }

                if (currentPhotoObject != null) {
                    drawImageButton = webPage.type != null && (webPage.type.equals("photo") || webPage.type.equals("document") || webPage.type.equals("gif"));
                    if (linkPreviewHeight != 0) {
                        linkPreviewHeight += AndroidUtilities.dp(2);
                        totalHeight += AndroidUtilities.dp(2);
                    }

                    maxChildWidth = Math.max(maxChildWidth, maxPhotoWidth + additinalWidth);
                    currentPhotoObject.size = -1;
                    if (currentPhotoObjectThumb != null) {
                        currentPhotoObjectThumb.size = -1;
                    }

                    int width;
                    int height;
                    if (smallImage) {
                        width = height = maxPhotoWidth;
                    } else {
                        width = currentPhotoObject.w;
                        height = currentPhotoObject.h;
                        float scale = width / (float) maxPhotoWidth;
                        width /= scale;
                        height /= scale;
                        if (webPage.site_name == null || webPage.site_name != null && !webPage.site_name.toLowerCase().equals("instagram") && !isGifDocument) {
                            if (height > AndroidUtilities.displaySize.y / 3) {
                                height = AndroidUtilities.displaySize.y / 3;
                            }
                        }
                    }
                    if (isSmallImage) {
                        if (AndroidUtilities.dp(50) + additionalHeight > linkPreviewHeight) {
                            totalHeight += AndroidUtilities.dp(50) + additionalHeight - linkPreviewHeight + AndroidUtilities.dp(8);
                            linkPreviewHeight = AndroidUtilities.dp(50) + additionalHeight;
                        }
                        linkPreviewHeight -= AndroidUtilities.dp(8);
                    } else {
                        totalHeight += height + AndroidUtilities.dp(12);
                        linkPreviewHeight += height;
                    }

                    linkImageView.setImageCoords(0, 0, width, height);

                    currentPhotoFilter = String.format(Locale.US, "%d_%d", width, height);
                    currentPhotoFilterThumb = String.format(Locale.US, "%d_%d_b", width, height);

                    if (isGifDocument) {
                        boolean photoExist = true;
                        File cacheFile = FileLoader.getPathToAttach(webPage.document);
                        if (!cacheFile.exists()) {
                            photoExist = false;
                        }
                        String fileName = FileLoader.getAttachFileName(webPage.document);
                        if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF) || FileLoader.getInstance().isLoadingFile(fileName)) {
                            photoNotSet = false;
                            linkImageView.setImage(webPage.document, null, currentPhotoObject.location, currentPhotoFilter, webPage.document.size, null, false);
                        } else {
                            photoNotSet = true;
                            linkImageView.setImage(null, null, currentPhotoObject.location, currentPhotoFilter, 0, null, false);
                        }
                    } else {
                        boolean photoExist = true;
                        File cacheFile = FileLoader.getPathToAttach(currentPhotoObject, true);
                        if (!cacheFile.exists()) {
                            photoExist = false;
                        }
                        String fileName = FileLoader.getAttachFileName(currentPhotoObject);
                        if (photoExist || MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) || FileLoader.getInstance().isLoadingFile(fileName)) {
                            photoNotSet = false;
                            linkImageView.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, false);
                        } else {
                            photoNotSet = true;
                            if (currentPhotoObjectThumb != null) {
                                linkImageView.setImage(null, null, currentPhotoObjectThumb.location, String.format(Locale.US, "%d_%d_b", width, height), 0, null, false);
                            } else {
                                linkImageView.setImageBitmap((Drawable) null);
                            }
                        }
                    }
                    drawLinkImageView = true;

                    if (webPage.site_name != null) {
                        if (webPage.site_name.toLowerCase().equals("instagram") && webPage.type != null && webPage.type.equals("video")) {
                            isInstagram = true;
                            if (igvideoDrawable == null) {
                                igvideoDrawable = getResources().getDrawable(R.drawable.igvideo);
                            }
                        }
                    }

                    if (webPage.type != null && webPage.type.equals("video") && webPage.duration != 0) {
                        if (durationPaint == null) {
                            durationPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
                            durationPaint.setTextSize(AndroidUtilities.dp(12));
                            durationPaint.setColor(0xffffffff);
                        }
                        int minutes = webPage.duration / 60;
                        int seconds = webPage.duration - minutes * 60;
                        String str = String.format("%d:%02d", minutes, seconds);
                        durationWidth = (int) Math.ceil(durationPaint.measureText(str));
                        durationLayout = new StaticLayout(str, durationPaint, durationWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    }
                } else {
                    linkImageView.setImageBitmap((Drawable) null);
                    linkPreviewHeight -= AndroidUtilities.dp(6);
                    totalHeight += AndroidUtilities.dp(4);
                }
            } else {
                linkImageView.setImageBitmap((Drawable) null);
            }

            if (hasLinkPreview || maxWidth - messageObject.lastLineWidth < timeMore) {
                totalHeight += AndroidUtilities.dp(14);
                backgroundWidth = Math.max(maxChildWidth, messageObject.lastLineWidth) + AndroidUtilities.dp(29);
            } else {
                int diff = maxChildWidth - messageObject.lastLineWidth;
                if (diff >= 0 && diff <= timeMore) {
                    backgroundWidth = maxChildWidth + timeMore - diff + AndroidUtilities.dp(29);
                } else {
                    backgroundWidth = Math.max(maxChildWidth, messageObject.lastLineWidth + timeMore) + AndroidUtilities.dp(29);
                }
            }
        }
        updateButtonState(dataChanged);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), totalHeight);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);

        if (currentMessageObject.isOutOwner()) {
            textX = layoutWidth - backgroundWidth + AndroidUtilities.dp(10);
            textY = AndroidUtilities.dp(10) + namesOffset;
        } else {
            textX = AndroidUtilities.dp(19) + (isChat && currentMessageObject.messageOwner.from_id > 0 ? AndroidUtilities.dp(52) : 0);
            textY = AndroidUtilities.dp(10) + namesOffset;
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentMessageObject == null || currentMessageObject.textLayoutBlocks == null || currentMessageObject.textLayoutBlocks.isEmpty()) {
            return;
        }

        if (currentMessageObject.isOutOwner()) {
            textX = layoutWidth - backgroundWidth + AndroidUtilities.dp(10);
            textY = AndroidUtilities.dp(10) + namesOffset;
        } else {
            textX = AndroidUtilities.dp(19) + (isChat && currentMessageObject.messageOwner.from_id > 0 ? AndroidUtilities.dp(52) : 0);
            textY = AndroidUtilities.dp(10) + namesOffset;
        }

        if (firstVisibleBlockNum >= 0) {
            for (int a = firstVisibleBlockNum; a <= lastVisibleBlockNum; a++) {
                if (a >= currentMessageObject.textLayoutBlocks.size()) {
                    break;
                }
                MessageObject.TextLayoutBlock block = currentMessageObject.textLayoutBlocks.get(a);
                canvas.save();
                canvas.translate(textX - (int) Math.ceil(block.textXOffset), textY + block.textYOffset);
                if (pressedLink != null && a == linkBlockNum) {
                    canvas.drawPath(urlPath, urlPaint);
                }
                try {
                    block.textLayout.draw(canvas);
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                }
                canvas.restore();
            }
        }

        if (hasLinkPreview) {
            int startY = textY + currentMessageObject.textHeight + AndroidUtilities.dp(8);
            int linkPreviewY = startY;
            int smallImageStartY = 0;
            replyLinePaint.setColor(currentMessageObject.isOutOwner() ? 0xff8dc97a : 0xff6c9fd2);

            canvas.drawRect(textX, linkPreviewY - AndroidUtilities.dp(3), textX + AndroidUtilities.dp(2), linkPreviewY + linkPreviewHeight + AndroidUtilities.dp(3), replyLinePaint);

            if (siteNameLayout != null) {
                replyNamePaint.setColor(currentMessageObject.isOutOwner() ? 0xff70b15c : 0xff4b91cf);
                canvas.save();
                canvas.translate(textX + AndroidUtilities.dp(10), linkPreviewY - AndroidUtilities.dp(3));
                siteNameLayout.draw(canvas);
                canvas.restore();
                linkPreviewY += siteNameLayout.getLineBottom(siteNameLayout.getLineCount() - 1);
            }

            if (titleLayout != null) {
                if (linkPreviewY != startY) {
                    linkPreviewY += AndroidUtilities.dp(2);
                }
                replyNamePaint.setColor(0xff000000);
                smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
                canvas.save();
                canvas.translate(textX + AndroidUtilities.dp(10) + titleX, linkPreviewY - AndroidUtilities.dp(3));
                titleLayout.draw(canvas);
                canvas.restore();
                linkPreviewY += titleLayout.getLineBottom(titleLayout.getLineCount() - 1);
            }

            if (authorLayout != null) {
                if (linkPreviewY != startY) {
                    linkPreviewY += AndroidUtilities.dp(2);
                }
                if (smallImageStartY == 0) {
                    smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
                }
                replyNamePaint.setColor(0xff000000);
                canvas.save();
                canvas.translate(textX + AndroidUtilities.dp(10) + authorX, linkPreviewY - AndroidUtilities.dp(3));
                authorLayout.draw(canvas);
                canvas.restore();
                linkPreviewY += authorLayout.getLineBottom(authorLayout.getLineCount() - 1);
            }

            if (descriptionLayout != null) {
                if (linkPreviewY != startY) {
                    linkPreviewY += AndroidUtilities.dp(2);
                }
                if (smallImageStartY == 0) {
                    smallImageStartY = linkPreviewY - AndroidUtilities.dp(1);
                }
                replyTextPaint.setColor(0xff000000);
                descriptionY = linkPreviewY - AndroidUtilities.dp(3);
                canvas.save();
                canvas.translate(textX + AndroidUtilities.dp(10) + descriptionX, descriptionY);
                if (pressedLink != null && linkBlockNum == -10) {
                    canvas.drawPath(urlPath, urlPaint);
                }
                descriptionLayout.draw(canvas);
                canvas.restore();
                linkPreviewY += descriptionLayout.getLineBottom(descriptionLayout.getLineCount() - 1);
            }

            if (drawLinkImageView) {
                if (linkPreviewY != startY) {
                    linkPreviewY += AndroidUtilities.dp(2);
                }

                if (isSmallImage) {
                    linkImageView.setImageCoords(textX + backgroundWidth - AndroidUtilities.dp(77), smallImageStartY, linkImageView.getImageWidth(), linkImageView.getImageHeight());
                } else {
                    linkImageView.setImageCoords(textX + AndroidUtilities.dp(10), linkPreviewY, linkImageView.getImageWidth(), linkImageView.getImageHeight());
                    if (drawImageButton) {
                        int size = AndroidUtilities.dp(48);
                        buttonX = (int) (linkImageView.getImageX() + (linkImageView.getImageWidth() - size) / 2.0f);
                        buttonY = (int) (linkImageView.getImageY() + (linkImageView.getImageHeight() - size) / 2.0f);
                        radialProgress.setProgressRect(buttonX, buttonY, buttonX + AndroidUtilities.dp(48), buttonY + AndroidUtilities.dp(48));
                    }
                }
                linkImageView.draw(canvas);
                if (drawImageButton) {
                    radialProgress.draw(canvas);
                }

                if (isInstagram && igvideoDrawable != null) {
                    int x = linkImageView.getImageX() + linkImageView.getImageWidth() - igvideoDrawable.getIntrinsicWidth() - AndroidUtilities.dp(4);
                    int y = linkImageView.getImageY() + AndroidUtilities.dp(4);
                    igvideoDrawable.setBounds(x, y, x + igvideoDrawable.getIntrinsicWidth(), y + igvideoDrawable.getIntrinsicHeight());
                    igvideoDrawable.draw(canvas);
                }

                if (durationLayout != null) {
                    int x = linkImageView.getImageX() + linkImageView.getImageWidth() - AndroidUtilities.dp(8) - durationWidth;
                    int y = linkImageView.getImageY() + linkImageView.getImageHeight() - AndroidUtilities.dp(19);
                    ResourceLoader.mediaBackgroundDrawable.setBounds(x - AndroidUtilities.dp(4), y - AndroidUtilities.dp(1.5f), x + durationWidth + AndroidUtilities.dp(4), y + AndroidUtilities.dp(14.5f));
                    ResourceLoader.mediaBackgroundDrawable.draw(canvas);

                    canvas.save();
                    canvas.translate(x, y);
                    durationLayout.draw(canvas);
                    canvas.restore();
                }
            }
        }
    }

    private Drawable getDrawableForCurrentState() {
        if (buttonState >= 0 && buttonState < 4) {
            if (buttonState == 1) {
                return ResourceLoader.buttonStatesDrawables[4];
            } else {
                return ResourceLoader.buttonStatesDrawables[buttonState];
            }
        }
        return null;
    }

    public void updateButtonState(boolean animated) {
        if (currentPhotoObject == null || !drawImageButton) {
            return;
        }
        String fileName;
        File cacheFile;

        if (isGifDocument) {
            fileName = FileLoader.getAttachFileName(currentMessageObject.messageOwner.media.webpage.document);
            cacheFile = FileLoader.getPathToAttach(currentMessageObject.messageOwner.media.webpage.document);
        } else {
            fileName = FileLoader.getAttachFileName(currentPhotoObject);
            cacheFile = FileLoader.getPathToAttach(currentPhotoObject, true);
        }
        if (fileName == null) {
            radialProgress.setBackground(null, false, false);
            return;
        }
        if (!cacheFile.exists()) {
            MediaController.getInstance().addLoadingFileObserver(fileName, this);
            float setProgress = 0;
            boolean progressVisible = false;
            if (!FileLoader.getInstance().isLoadingFile(fileName)) {
                if (!cancelLoading &&
                    (!isGifDocument && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_PHOTO) ||
                    isGifDocument && MediaController.getInstance().canDownloadMedia(MediaController.AUTODOWNLOAD_MASK_GIF)) ) {
                    progressVisible = true;
                    buttonState = 1;
                } else {
                    buttonState = 0;
                }
            } else {
                progressVisible = true;
                buttonState = 1;
                Float progress = ImageLoader.getInstance().getFileProgress(fileName);
                setProgress = progress != null ? progress : 0;
            }
            radialProgress.setProgress(setProgress, false);
            radialProgress.setBackground(getDrawableForCurrentState(), progressVisible, animated);
            invalidate();
        } else {
            MediaController.getInstance().removeLoadingFileObserver(this);
            if (isGifDocument && !linkImageView.isAllowStartAnimation()) {
                buttonState = 2;
            } else {
                buttonState = -1;
            }
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
            invalidate();
        }
    }

    private void didPressedButton(boolean animated) {
        if (buttonState == 0) {
            cancelLoading = false;
            radialProgress.setProgress(0, false);
            if (isGifDocument) {
                linkImageView.setImage(currentMessageObject.messageOwner.media.webpage.document, null, currentPhotoObject.location, currentPhotoFilter, currentMessageObject.messageOwner.media.webpage.document.size, null, false);
                currentMessageObject.audioProgress = 2;
            } else {
                linkImageView.setImage(currentPhotoObject.location, currentPhotoFilter, currentPhotoObjectThumb != null ? currentPhotoObjectThumb.location : null, currentPhotoFilterThumb, 0, null, false);
            }
            buttonState = 1;
            radialProgress.setBackground(getDrawableForCurrentState(), true, animated);
            invalidate();
        } else if (buttonState == 1) {
            if (currentMessageObject.isOut() && currentMessageObject.isSending()) {
                if (delegate != null) {
                    delegate.didPressedCancelSendButton(this);
                }
            } else {
                cancelLoading = true;
                linkImageView.cancelLoadImage();
                buttonState = 0;
                radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
                invalidate();
            }
        } else if (buttonState == 2) {
            linkImageView.setAllowStartAnimation(true);
            linkImageView.startAnimation();
            currentMessageObject.audioProgress = 0;
            buttonState = -1;
            radialProgress.setBackground(getDrawableForCurrentState(), false, animated);
        }
    }

    @Override
    public void onFailedDownload(String fileName) {
        updateButtonState(false);
    }

    @Override
    public void onSuccessDownload(String fileName) {
        radialProgress.setProgress(1, true);
        if (isGifDocument && currentMessageObject.audioProgress != 1) {
            buttonState = 2;
            didPressedButton(true);
        } else if (!photoNotSet) {
            updateButtonState(true);
        } else {
            setMessageObject(currentMessageObject);
        }
    }

    @Override
    public void onProgressDownload(String fileName, float progress) {
        radialProgress.setProgress(progress, true);
        if (buttonState != 1) {
            updateButtonState(false);
        }
    }

    @Override
    public void onProvideStructure(ViewStructure structure) {
        super.onProvideStructure(structure);
        if (allowAssistant && Build.VERSION.SDK_INT >= 23) {
            structure.setText(currentMessageObject.messageText);
        }
    }
}
