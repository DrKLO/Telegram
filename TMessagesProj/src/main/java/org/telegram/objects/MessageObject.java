/*
 * This is the source code of Telegram for Android v. 1.3.2.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013.
 */

package org.telegram.objects;

import android.graphics.Bitmap;
import android.graphics.Paint;
import android.text.Layout;
import android.text.Spannable;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.util.Linkify;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.Utilities;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class MessageObject {
    public TLRPC.Message messageOwner;
    public CharSequence messageText;
    public int type;
    public int contentType;
    public ArrayList<PhotoObject> photoThumbs;
    public Bitmap imagePreview;
    public PhotoObject previewPhoto;
    public String dateKey;
    public boolean deleted = false;
    public float audioProgress;
    public int audioProgressSec;

    private static TextPaint textPaint;
    public int lastLineWidth;
    public int textWidth;
    public int textHeight;
    public int blockHeight = Integer.MAX_VALUE;

    public static class TextLayoutBlock {
        public StaticLayout textLayout;
        public float textXOffset = 0;
        public float textYOffset = 0;
        public int charactersOffset = 0;
    }

    private static final int LINES_PER_BLOCK = 10;

    public ArrayList<TextLayoutBlock> textLayoutBlocks;

    public MessageObject(TLRPC.Message message, AbstractMap<Integer, TLRPC.User> users) {
        if (textPaint == null) {
            textPaint = new TextPaint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(0xff000000);
            textPaint.linkColor = 0xff316f9f;
        }

        messageOwner = message;
        messageText = Emoji.replaceEmoji(message.generateMessageText(users));
        photoThumbs = new ArrayList<PhotoObject>(message.getPhotoThumbs());
        imagePreview = message.getImagePreview();

        if (message instanceof TLRPC.TL_message || (message instanceof TLRPC.TL_messageForwarded && (message.media == null || !(message.media instanceof TLRPC.TL_messageMediaEmpty)))) {
            if (message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty) {
                contentType = type = 0;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaPhoto) {
                contentType = type = 1;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaGeo) {
                if (message.isFromMe()) {
                    contentType = type = 4;
                } else {
                    contentType = type = 5;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaVideo) {
                if (message.isFromMe()) {
                    contentType = type = 6;
                } else {
                    contentType = type = 7;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaContact) {
                if (message.isFromMe()) {
                    contentType = type = 12;
                } else {
                    contentType = type = 13;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                contentType = type = 0;
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaDocument) {
                if (message.media.document.thumb != null && !(message.media.document.thumb instanceof TLRPC.TL_photoSizeEmpty) && message.media.document.mime_type != null && message.media.document.mime_type.equals("image/gif")) {
                    contentType = 1;
                    type = 8;
                } else {
                    if (message.isFromMe()) {
                        contentType = type = 8;
                    } else {
                        contentType = type = 9;
                    }
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaAudio) {
                contentType = type = 2;
            }
        } else if (message instanceof TLRPC.TL_messageService) {
            if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                contentType = type = 0;
            } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto || message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                contentType = type = 11;
            } else {
                contentType = type = 10;
            }
        } else if (message instanceof TLRPC.TL_messageForwarded) {
            contentType = type = 0;
        }

        Calendar rightNow = new GregorianCalendar();
        rightNow.setTimeInMillis((long)(messageOwner.date) * 1000);
        int dateDay = rightNow.get(Calendar.DAY_OF_YEAR);
        int dateYear = rightNow.get(Calendar.YEAR);
        int dateMonth = rightNow.get(Calendar.MONTH);
        dateKey = String.format("%d_%02d_%02d", dateYear, dateMonth, dateDay);

        generateLayout();
    }

    public String getFileName() {
        if (messageOwner.media instanceof TLRPC.TL_messageMediaWithFile) {
            return ((TLRPC.TL_messageMediaWithFile)messageOwner.media).getFileName();
        }
        return "";
    }

    private void generateLayout() {
        if (type != 0 && type != 1 && type != 8 && type != 9 || messageOwner.to_id == null || messageText == null || messageText.length() == 0) {
            return;
        }

        textLayoutBlocks = new ArrayList<TextLayoutBlock>();

        if (messageText instanceof Spannable) {
            if (messageOwner.message != null && messageOwner.message.contains(".") && (messageOwner.message.contains(".com") || messageOwner.message.contains("http") || messageOwner.message.contains(".ru") || messageOwner.message.contains(".org") || messageOwner.message.contains(".net"))) {
                Linkify.addLinks((Spannable)messageText, Linkify.WEB_URLS);
            } else if (messageText.length() < 100) {
                Linkify.addLinks((Spannable)messageText, Linkify.WEB_URLS | Linkify.EMAIL_ADDRESSES | Linkify.PHONE_NUMBERS);
            }
        }

        textPaint.setTextSize(Utilities.dp(MessagesController.getInstance().fontSize));

        int maxWidth;
        if (messageOwner.to_id.chat_id != 0) {
            maxWidth = Math.min(Utilities.displaySize.x, Utilities.displaySize.y) - Utilities.dp(122);
        } else {
            maxWidth = Math.min(Utilities.displaySize.x, Utilities.displaySize.y) - Utilities.dp(80);
        }

        StaticLayout textLayout = null;

        try {
            textLayout = new StaticLayout(messageText, textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
        } catch (Exception e) {
            FileLog.e("tmessages", e);
            return;
        }

        textHeight = textLayout.getHeight();
        int linesCount = textLayout.getLineCount();

        int blocksCount = (int)Math.ceil((float)linesCount / LINES_PER_BLOCK);
        int linesOffset = 0;

        for (int a = 0; a < blocksCount; a++) {

            int currentBlockLinesCount = Math.min(LINES_PER_BLOCK, linesCount - linesOffset);
            TextLayoutBlock block = new TextLayoutBlock();

            if (blocksCount == 1) {
                block.textLayout = textLayout;
                block.textYOffset = 0;
                block.charactersOffset = 0;
                blockHeight = textHeight;
            } else {
                int startCharacter = textLayout.getLineStart(linesOffset);
                int endCharacter = textLayout.getLineEnd(linesOffset + currentBlockLinesCount - 1);
                if (endCharacter < startCharacter) {
                    continue;
                }
                block.charactersOffset = startCharacter;
                try {
                    CharSequence str = messageText.subSequence(startCharacter, endCharacter);
                    block.textLayout = new StaticLayout(str, textPaint, maxWidth, Layout.Alignment.ALIGN_NORMAL, 1.0f, 0.0f, false);
                    block.textYOffset = textLayout.getLineTop(linesOffset);
                    if (a != blocksCount - 1) {
                        blockHeight = Math.min(blockHeight, block.textLayout.getHeight());
                    }
                } catch (Exception e) {
                    FileLog.e("tmessages", e);
                    continue;
                }
            }

            textLayoutBlocks.add(block);

            float lastLeft = block.textXOffset = 0;
            try {
                lastLeft = block.textXOffset = block.textLayout.getLineLeft(currentBlockLinesCount - 1);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            float lastLine = 0;
            try {
                lastLine = block.textLayout.getLineWidth(currentBlockLinesCount - 1);
            } catch (Exception e) {
                FileLog.e("tmessages", e);
            }

            int linesMaxWidth;
            int lastLineWidthWithLeft;
            int linesMaxWidthWithLeft;
            boolean hasNonRTL = false;

            linesMaxWidth = (int)Math.ceil(lastLine);

            if (a == blocksCount - 1) {
                lastLineWidth = linesMaxWidth;
            }

            linesMaxWidthWithLeft = lastLineWidthWithLeft = (int)Math.ceil(lastLine + lastLeft);
            if (lastLeft == 0) {
                hasNonRTL = true;
            }

            if (currentBlockLinesCount > 1) {
                float textRealMaxWidth = 0, textRealMaxWidthWithLeft = 0, lineWidth, lineLeft;
                for (int n = 0; n < currentBlockLinesCount; ++n) {
                    try {
                        lineWidth = block.textLayout.getLineWidth(n);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        lineWidth = 0;
                    }

                    try {
                        lineLeft = block.textLayout.getLineLeft(n);
                    } catch (Exception e) {
                        FileLog.e("tmessages", e);
                        lineLeft = 0;
                    }

                    block.textXOffset = Math.min(block.textXOffset, lineLeft);

                    if (lineLeft == 0) {
                        hasNonRTL = true;
                    }
                    textRealMaxWidth = Math.max(textRealMaxWidth, lineWidth);
                    textRealMaxWidthWithLeft = Math.max(textRealMaxWidthWithLeft, lineWidth + lineLeft);
                    linesMaxWidth = Math.max(linesMaxWidth, (int)Math.ceil(lineWidth));
                    linesMaxWidthWithLeft = Math.max(linesMaxWidthWithLeft, (int)Math.ceil(lineWidth + lineLeft));
                }
                if (hasNonRTL) {
                    textRealMaxWidth = textRealMaxWidthWithLeft;
                    if (a == blocksCount - 1) {
                        lastLineWidth = lastLineWidthWithLeft;
                    }
                    linesMaxWidth = linesMaxWidthWithLeft;
                } else if (a == blocksCount - 1) {
                    lastLineWidth = linesMaxWidth;
                }
                textWidth = Math.max(textWidth, (int)Math.ceil(textRealMaxWidth));
            } else {
                textWidth = Math.max(textWidth, Math.min(maxWidth, linesMaxWidth));
            }

            if (hasNonRTL) {
                block.textXOffset = 0;
            }

            linesOffset += currentBlockLinesCount;
        }
    }
}
