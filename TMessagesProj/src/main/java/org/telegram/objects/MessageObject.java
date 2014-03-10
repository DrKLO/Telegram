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

import org.telegram.messenger.ConnectionsManager;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.TLObject;
import org.telegram.messenger.TLRPC;
import org.telegram.messenger.Emoji;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ApplicationLoader;

import java.io.File;
import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;

public class MessageObject {
    public TLRPC.Message messageOwner;
    public CharSequence messageText;
    public int type;
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

        if (message instanceof TLRPC.TL_messageService) {
            if (message.action != null) {
                TLRPC.User fromUser = users.get(message.from_id);
                if (fromUser == null) {
                    fromUser = MessagesController.Instance.users.get(message.from_id);
                }
                if (message.action instanceof TLRPC.TL_messageActionChatCreate) {
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionYouCreateGroup);
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionCreateGroup).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionCreateGroup).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeleteUser) {
                    if (message.action.user_id == message.from_id) {
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionYouLeftUser);
                        } else {
                            if (fromUser != null) {
                                messageText = ApplicationLoader.applicationContext.getString(R.string.ActionLeftUser).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                            } else {
                                messageText = ApplicationLoader.applicationContext.getString(R.string.ActionLeftUser).replace("un1", "");
                            }
                        }
                    } else {
                        TLRPC.User who = users.get(message.action.user_id);
                        if (who == null) {
                            MessagesController.Instance.users.get(message.action.user_id);
                        }
                        if (who != null && fromUser != null) {
                            if (message.from_id == UserConfig.clientUserId) {
                                messageText = ApplicationLoader.applicationContext.getString(R.string.ActionYouKickUser).replace("un2", Utilities.formatName(who.first_name, who.last_name));
                            } else if (message.action.user_id == UserConfig.clientUserId) {
                                messageText = ApplicationLoader.applicationContext.getString(R.string.ActionKickUserYou).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                            } else {
                                messageText = ApplicationLoader.applicationContext.getString(R.string.ActionKickUser).replace("un2", Utilities.formatName(who.first_name, who.last_name)).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                            }
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionKickUser).replace("un2", "").replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatAddUser) {
                    TLRPC.User whoUser = users.get(message.action.user_id);
                    if (whoUser == null) {
                        MessagesController.Instance.users.get(message.action.user_id);
                    }
                    if (whoUser != null && fromUser != null) {
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionYouAddUser).replace("un2", Utilities.formatName(whoUser.first_name, whoUser.last_name));
                        } else if (message.action.user_id == UserConfig.clientUserId) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionAddUserYou).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionAddUser).replace("un2", Utilities.formatName(whoUser.first_name, whoUser.last_name)).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        }
                    } else {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionAddUser).replace("un2", "").replace("un1", "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto) {
                    photoThumbs = new ArrayList<PhotoObject>();
                    for (TLRPC.PhotoSize size : message.action.photo.sizes) {
                        photoThumbs.add(new PhotoObject(size));
                    }
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionYouChangedPhoto);
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedPhoto).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatEditTitle) {
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionYouChangedTitle).replace("un2", message.action.title);
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedTitle).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name)).replace("un2", message.action.title);
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionChangedTitle).replace("un1", "").replace("un2", message.action.title);
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionChatDeletePhoto) {
                    if (message.from_id == UserConfig.clientUserId) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.ActionYouRemovedPhoto);
                    } else {
                        if (fromUser != null) {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionRemovedPhoto).replace("un1", Utilities.formatName(fromUser.first_name, fromUser.last_name));
                        } else {
                            messageText = ApplicationLoader.applicationContext.getString(R.string.ActionRemovedPhoto).replace("un1", "");
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionTTLChange) {
                    if (message.action.ttl != 0) {
                        String timeString;
                        if (message.action.ttl == 2) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime2s);
                        } else if (message.action.ttl == 5) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime5s);
                        } else if (message.action.ttl == 60) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1m);
                        } else if (message.action.ttl == 60 * 60) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1h);
                        } else if (message.action.ttl == 60 * 60 * 24) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1d);
                        } else if (message.action.ttl == 60 * 60 * 24 * 7) {
                            timeString = ApplicationLoader.applicationContext.getString(R.string.MessageLifetime1w);
                        } else {
                            timeString = String.format("%d", message.action.ttl);
                        }
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeChangedOutgoing), timeString);
                        } else {
                            if (fromUser != null) {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeChanged), fromUser.first_name, timeString);
                            } else {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeChanged), "", timeString);
                            }
                        }
                    } else {
                        if (message.from_id == UserConfig.clientUserId) {
                            messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeYouRemoved));
                        } else {
                            if (fromUser != null) {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeRemoved), fromUser.first_name);
                            } else {
                                messageText = String.format(ApplicationLoader.applicationContext.getString(R.string.MessageLifetimeRemoved), "");
                            }
                        }
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                    String date = String.format("%s %s %s", Utilities.formatterYear.format(((long)message.date) * 1000), ApplicationLoader.applicationContext.getString(R.string.OtherAt), Utilities.formatterDay.format(((long)message.date) * 1000));
                    messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationUnrecognizedDevice, UserConfig.currentUser.first_name, date, message.action.title, message.action.address);
                } else if (message.action instanceof TLRPC.TL_messageActionUserJoined) {
                    if (fromUser != null) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactJoined, Utilities.formatName(fromUser.first_name, fromUser.last_name));
                    } else {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactJoined, "");
                    }
                } else if (message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                    if (fromUser != null) {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactNewPhoto, Utilities.formatName(fromUser.first_name, fromUser.last_name));
                    } else {
                        messageText = ApplicationLoader.applicationContext.getString(R.string.NotificationContactNewPhoto, "");
                    }
                }
            }
        } else if (message.media != null && !(message.media instanceof TLRPC.TL_messageMediaEmpty)) {
            if (message.media instanceof TLRPC.TL_messageMediaPhoto) {
                photoThumbs = new ArrayList<PhotoObject>();
                for (TLRPC.PhotoSize size : message.media.photo.sizes) {
                    PhotoObject obj = new PhotoObject(size);
                    photoThumbs.add(obj);
                    if (imagePreview == null && obj.image != null) {
                        imagePreview = obj.image;
                    }
                }
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachPhoto);
            } else if (message.media instanceof TLRPC.TL_messageMediaVideo) {
                photoThumbs = new ArrayList<PhotoObject>();
                PhotoObject obj = new PhotoObject(message.media.video.thumb);
                photoThumbs.add(obj);
                if (imagePreview == null && obj.image != null) {
                    imagePreview = obj.image;
                }
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachVideo);
            } else if (message.media instanceof TLRPC.TL_messageMediaGeo) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachLocation);
            } else if (message.media instanceof TLRPC.TL_messageMediaContact) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachContact);
            } else if (message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.UnsuppotedMedia);
            } else if (message.media instanceof TLRPC.TL_messageMediaDocument) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachDocument);
            } else if (message.media instanceof TLRPC.TL_messageMediaAudio) {
                messageText = ApplicationLoader.applicationContext.getString(R.string.AttachAudio);
            }
        } else {
            messageText = message.message;
        }
        messageText = Emoji.replaceEmoji(messageText);

        if (message instanceof TLRPC.TL_message || (message instanceof TLRPC.TL_messageForwarded && (message.media == null || !(message.media instanceof TLRPC.TL_messageMediaEmpty)))) {
            if (message.media == null || message.media instanceof TLRPC.TL_messageMediaEmpty) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 0;
                } else {
                    type = 1;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaPhoto) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 2;
                } else {
                    type = 3;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaGeo) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 4;
                } else {
                    type = 5;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaVideo) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 6;
                } else {
                    type = 7;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaContact) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 12;
                } else {
                    type = 13;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaUnsupported) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 0;
                } else {
                    type = 1;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaDocument) {
                if (message.from_id == UserConfig.clientUserId) {
                    type = 16;
                } else {
                    type = 17;
                }
            } else if (message.media != null && message.media instanceof TLRPC.TL_messageMediaAudio) {
                if (message.from_id == UserConfig.clientUserId) {
                    if (ConnectionsManager.enableAudio) {
                        type = 18;
                    } else {
                        type = 0;
                    }
                } else {
                    if (ConnectionsManager.enableAudio) {
                        type = 19;
                    } else {
                        type = 1;
                    }
                }
            }
        } else if (message instanceof TLRPC.TL_messageService) {
            if (message.action instanceof TLRPC.TL_messageActionLoginUnknownLocation) {
                type = 1;
            } else if (message.action instanceof TLRPC.TL_messageActionChatEditPhoto || message.action instanceof TLRPC.TL_messageActionUserUpdatedPhoto) {
                type = 11;
            } else {
                type = 10;
            }
        } else if (message instanceof TLRPC.TL_messageForwarded) {
            if (message.from_id == UserConfig.clientUserId) {
                type = 8;
            } else {
                type = 9;
            }
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
        if (messageOwner.media instanceof TLRPC.TL_messageMediaVideo) {
            return getAttachFileName(messageOwner.media.video);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaDocument) {
            return getAttachFileName(messageOwner.media.document);
        } else if (messageOwner.media instanceof TLRPC.TL_messageMediaAudio) {
            return getAttachFileName(messageOwner.media.audio);
        }
        return "";
    }

    public static String getAttachFileName(TLObject attach) {
        if (attach instanceof TLRPC.Video) {
            TLRPC.Video video = (TLRPC.Video)attach;
            return video.dc_id + "_" + video.id + ".mp4";
        } else if (attach instanceof TLRPC.Document) {
            TLRPC.Document document = (TLRPC.Document)attach;
            String ext = document.file_name;
            int idx = -1;
            if (ext == null || (idx = ext.lastIndexOf(".")) == -1) {
                ext = "";
            } else {
                ext = ext.substring(idx);
            }
            if (ext.length() > 1) {
                return document.dc_id + "_" + document.id + ext;
            } else {
                return document.dc_id + "_" + document.id;
            }
        } else if (attach instanceof TLRPC.PhotoSize) {
            TLRPC.PhotoSize photo = (TLRPC.PhotoSize)attach;
            return photo.location.volume_id + "_" + photo.location.local_id + ".jpg";
        } else if (attach instanceof TLRPC.Audio) {
            TLRPC.Audio audio = (TLRPC.Audio)attach;
            return audio.dc_id + "_" + audio.id + ".m4a";
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

        textPaint.setTextSize(Utilities.dp(MessagesController.Instance.fontSize));

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
